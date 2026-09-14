import logging
import re
import threading
import time
from contextlib import asynccontextmanager
from typing import Annotated

import httpx
import redis
from fastapi import Depends, FastAPI, Header, Query, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field, field_validator
from sqlalchemy import select
from sqlalchemy import text as sql_text

from sky_ai.agent import CustomerAgent
from sky_ai.config import Settings
from sky_ai.database import Store, api_timestamp, tool_calls, uid
from sky_ai.intent import CustomerIntent
from sky_ai.security import ApiError, IdentityBridge
from sky_ai.text import contains_any
from sky_ai.tools import BusinessTools

logger = logging.getLogger(__name__)


class CreateConversation(BaseModel):
    model_config = ConfigDict(extra="forbid")
    title: str | None = Field(None, max_length=100)


class Chat(BaseModel):
    model_config = ConfigDict(extra="forbid")
    conversationId: str = Field(min_length=1, max_length=64)
    message: str = Field(min_length=1, max_length=2000)
    clientRequestId: str = Field(min_length=1, max_length=64, pattern=r"^[A-Za-z0-9._:-]+$")

    @field_validator("message")
    @classmethod
    def not_blank(cls, value):
        """拒绝仅含空白字符的消息，合法输入保持原文以用于请求指纹计算。"""
        if not value.strip():  # Python 中空字符串 "" 被视为假值（falsy），not "" 就是 True
            raise ValueError("消息不能为空")
        return value


def timestamp(value):
    """将数据库中的 Asia/Shanghai 时间转换为带毫秒的 UTC 时间。"""
    return api_timestamp(value)


def conversation_view(row):
    """将数据库会话记录转换为兼容现有前端的驼峰字段响应。"""
    return {
        "conversationId": row["conversation_id"],
        "title": row["title"],
        "status": row["status"],
        "currentHandler": row["current_handler"],
        "lastMessageSequence": row["last_message_sequence"],
        "lastMessageTime": timestamp(row["last_message_time"]),
        "createdAt": timestamp(row["create_time"]),
        "updatedAt": timestamp(row["update_time"]),
    }


def message_view(row):
    """将数据库消息记录转换为前端历史消息视图，包含状态、错误码和时间。"""
    return {
        "messageId": row["message_id"],
        "sequenceNo": row["sequence_no"],
        "role": row["role"],
        "content": row["content"],
        "status": row["status"],
        "errorCode": row["error_code"],
        "createdAt": timestamp(row["create_time"]),
        "updatedAt": timestamp(row["update_time"]),
    }


def create_app(
    settings=None,
    store=None,
    redis_client=None,
    tool_client=None,
    model=None,
    intent_classifier=None,
):
    """组装 FastAPI 应用、依赖资源和兼容旧服务的接口，并返回应用实例。

    可注入配置、存储、Redis、业务客户端、回答模型及意图分类器用于测试；未提供时使用配置创建。
    """
    if not settings:
        settings = Settings()
    if not store:
        store = Store(settings.sqlalchemy_url())
    if redis_client is None:
        redis_client = redis.Redis.from_url(
            settings.redis_url, socket_connect_timeout=1, socket_timeout=1, decode_responses=True
        )
    if not tool_client:
        tool_client = httpx.Client(
            base_url=settings.sky_server_base_url,
            timeout=settings.tool_timeout,
            follow_redirects=False,
        )
    identity = IdentityBridge(settings)
    agent = CustomerAgent(
        settings,
        store,
        redis_client,
        BusinessTools(tool_client, identity, store),
        model,
        intent_classifier,
    )
    worker_id = uid()

    @asynccontextmanager
    async def lifespan(app):
        """管理应用生命周期，在应用关闭时释放 HTTP、Redis 和数据库资源。"""
        recovered = store.expire_stale_turns()
        if recovered:
            logger.warning("Recovered %s expired AI turns during startup", recovered)
        yield
        tool_client.close()
        redis_client.close()
        store.engine.dispose()

    app = FastAPI(title="Sky AI Service", version="0.1.0", lifespan=lifespan)
    app.state.agent = agent
    app.state.store = store

    @app.middleware("http")
    async def trace_request(request: Request, call_next):
        """校验或生成本次请求的追踪 ID，并将其同时写入请求状态与响应头。"""
        supplied = request.headers.get("X-Trace-Id", "")
        if re.fullmatch(r"[A-Za-z0-9_-]{8,64}", supplied):
            request.state.trace_id = supplied
        else:
            request.state.trace_id = uid()
        response = await call_next(request)
        response.headers["X-Trace-Id"] = request.state.trace_id
        return response

    @app.exception_handler(ApiError)
    async def api_error(request, exc):
        """将已知业务异常转换为包含安全消息和追踪 ID 的统一 JSON 响应。"""
        return JSONResponse(
            status_code=exc.status,
            content={"code": exc.code, "message": exc.message, "traceId": request.state.trace_id},
        )

    @app.exception_handler(RequestValidationError)
    async def validation_error(request, exc):
        """将请求参数校验失败转换为兼容旧接口的 400 响应，不回显原始请求内容。"""
        return JSONResponse(
            status_code=400,
            content={
                "code": "INVALID_REQUEST",
                "message": "请求参数不正确",
                "traceId": request.state.trace_id,
            },
        )

    @app.exception_handler(Exception)
    async def unexpected_error(request, exc):
        # Do not log request contents, headers or exception strings that can contain credentials.
        """记录未预期异常的类型及追踪 ID，向用户返回不包含内部细节的 503 响应。"""
        logger.error(
            "Unhandled AI error type=%s trace=%s", type(exc).__name__, request.state.trace_id
        )
        return JSONResponse(
            status_code=503,
            content={
                "code": "INTERNAL_ERROR",
                "message": "客服暂时不可用，请稍后重试",
                "traceId": request.state.trace_id,
            },
        )

    def current_user(authentication: Annotated[str | None, Header()] = None):
        """从 authentication 请求头验证用户身份，返回供接口依赖注入的用户 ID。"""
        return identity.verify_user(authentication)

    user_dep = Depends(current_user)

    @app.get("/actuator/health")
    def health():
        """检查数据库连接和 Redis，可用时返回 UP，否则以 503 返回 DOWN。"""
        try:
            with store.engine.connect() as conn:
                conn.execute(sql_text("SELECT 1"))
            redis_client.ping()
            return {"status": "UP"}
        except Exception:
            return JSONResponse(status_code=503, content={"status": "DOWN"})

    @app.post("/api/ai/conversations", status_code=201)
    def create(body: CreateConversation, user: int = user_dep):
        """为认证用户创建标题可选的会话，并返回前端会话视图。"""
        return conversation_view(store.create(user, body.title))

    @app.get("/api/ai/conversations")
    def list_conversations(
        limit: Annotated[int, Query(ge=1, le=50)] = 20,
        offset: Annotated[int, Query(ge=0)] = 0,
        user: int = user_dep,
    ):
        """按 limit 和 offset 查询认证用户的会话，返回分页列表及后续页标记。"""
        rows = store.list(user, limit, offset)
        items = []
        for row in rows[:limit]:
            items.append(conversation_view(row))
        return {
            "items": items,
            "limit": limit,
            "offset": offset,
            "hasMore": len(rows) > limit,
        }

    @app.get("/api/ai/conversations/{cid}")
    def get_conversation(cid: str, user: int = user_dep):
        """返回认证用户拥有的指定会话视图，跨用户访问统一按不存在处理。"""
        return conversation_view(store.get(user, cid))

    @app.get("/api/ai/conversations/{cid}/messages")
    def get_messages(
        cid: str,
        afterSequence: Annotated[int, Query(ge=0)] = 0,
        limit: Annotated[int, Query(ge=1, le=100)] = 20,
        user: int = user_dep,
    ):
        """按 afterSequence 游标查询会话历史，返回消息列表、下一游标及后续页标记。"""
        rows = store.history(user, cid, afterSequence, limit)
        items = []
        for row in rows[:limit]:
            items.append(message_view(row))
        if items:
            next_sequence = items[-1]["sequenceNo"]
        else:
            next_sequence = afterSequence
        return {
            "items": items,
            "limit": limit,
            "afterSequence": afterSequence,
            "nextAfterSequence": next_sequence,
            "hasMore": len(rows) > limit,
        }

    def chat(user, cid, body, trace):
        """执行消息幂等占用、Agent 调用和结果持久化，或回放已有回答，返回统一聊天响应。

        user 为认证身份，cid 为会话 ID，body 包含消息和请求键，trace 关联本次调用。
        生成失败时将助手消息标记失败，并向接口层抛出可安全返回的 ApiError。
        """
        um, am, replayed = store.begin_turn(
            user,
            cid,
            body.message,
            body.clientRequestId,
            settings.provider,
            settings.model_name,
            trace,
            worker_id,
            settings.turn_lease_seconds,
        )
        started = time.monotonic()
        resolved_intent = None
        if not replayed:
            stop_heartbeat = threading.Event()
            lease_lost = threading.Event()

            def heartbeat():
                interval = max(5, settings.turn_lease_seconds // 3)
                while not stop_heartbeat.wait(interval):
                    try:
                        if not store.renew_lease(
                            am["message_id"], worker_id, settings.turn_lease_seconds
                        ):
                            lease_lost.set()
                            return
                    except Exception as exc:
                        logger.warning(
                            "AI lease renewal failed type=%s trace=%s",
                            type(exc).__name__,
                            trace,
                        )

            heartbeat_thread = threading.Thread(target=heartbeat, daemon=True)
            heartbeat_thread.start()
            try:
                history = []
                for row in store.context_messages(user, cid):
                    if row["message_id"] != um["message_id"]:
                        history.append(row)
                result = agent.invoke(user, cid, am["message_id"], trace, body.message, history)
                answer = result["answer"]
                resolved_intent = result["intent"]
                if lease_lost.is_set() or not store.finish(
                    am["message_id"],
                    answer,
                    am["version"],
                    worker_id,
                    latency=int((time.monotonic() - started) * 1000),
                ):
                    raise ApiError(409, "TURN_LEASE_LOST", "本次请求已过期，请重新发送")
            except Exception as exc:
                if isinstance(exc, ApiError):
                    error = exc
                else:
                    error = ApiError(503, "AI_UNAVAILABLE", "客服暂时不可用，请稍后重试")
                logger.warning("AI turn failed type=%s trace=%s", type(exc).__name__, trace)
                persisted = store.finish(
                    am["message_id"],
                    "",
                    am["version"],
                    worker_id,
                    error.code,
                    int((time.monotonic() - started) * 1000),
                )
                if not persisted:
                    error = ApiError(409, "TURN_LEASE_LOST", "本次请求已过期，请重新发送")
                raise error from None
            finally:
                stop_heartbeat.set()
                heartbeat_thread.join(timeout=1)
        else:
            answer = am["content"]
        with store.engine.connect() as conn:
            names = list(
                conn.execute(
                    select(tool_calls.c.tool_name)
                    .where(
                        tool_calls.c.message_id == am["message_id"],
                        tool_calls.c.result_status == "SUCCEEDED",
                    )
                    .order_by(tool_calls.c.id)
                ).scalars()
            )
        unique_names = []
        for name in names:
            if name not in unique_names:
                unique_names.append(name)
        tool = ",".join(unique_names)
        if not tool:
            tool = None
        fallback_intent = "GENERAL"
        if contains_any(
            body.message.lower(), ("退款", "退钱", "退费", "refund", "取消订单", "支付", "帮我下单")
        ) or "暂不支持退款、下单、支付或取消订单" in answer:
            fallback_intent = "UNSUPPORTED_WRITE"
        elif "订单 ID" in answer:
            fallback_intent = "ORDER_PROGRESS"
        elif contains_any(answer, ("预算", "价格范围", "清淡和重辣")):
            fallback_intent = "DISH_RECOMMENDATION"
        response_intent = {
            CustomerIntent.SHOP_STATUS_QUERY: "SHOP_STATUS",
            CustomerIntent.ORDER_PROGRESS_QUERY: "ORDER_PROGRESS",
            CustomerIntent.DISH_RECOMMENDATION: "DISH_RECOMMENDATION",
            CustomerIntent.UNSUPPORTED_WRITE: "UNSUPPORTED_WRITE",
            CustomerIntent.GENERAL: "GENERAL",
            CustomerIntent.AMBIGUOUS: "GENERAL",
            CustomerIntent.UNKNOWN: "GENERAL",
        }
        intent_by_tool = {
            "get_shop_status": "SHOP_STATUS",
            "get_order_progress": "ORDER_PROGRESS",
            "recommend_dishes": "DISH_RECOMMENDATION",
            "recommend_meal_combo": "DISH_RECOMMENDATION",
        }
        if names:
            first_tool_name = names[0]
        else:
            first_tool_name = ""
        if first_tool_name:
            intent = intent_by_tool.get(first_tool_name, fallback_intent)
        elif resolved_intent:
            intent = response_intent[CustomerIntent(resolved_intent)]
        else:
            intent = fallback_intent
        return {
            "answer": answer,
            "intent": intent,
            "toolUsed": tool,
            "provider": am["provider"],
            "traceId": trace,
            "userMessageId": um["message_id"],
            "assistantMessageId": am["message_id"],
            "replayed": replayed,
        }

    @app.post("/api/ai/poc/chat")
    def plain_chat(body: Chat, request: Request, user: int = user_dep):
        """处理普通 JSON 聊天请求，委托聊天流程执行并返回完整回答。"""
        return chat(user, body.conversationId, body, request.state.trace_id)

    return app
