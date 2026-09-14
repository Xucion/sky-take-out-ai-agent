import json
import time
from datetime import datetime, timedelta
from pathlib import Path

import httpx
from alembic import command
from alembic.config import Config
from langchain_core.messages import AIMessage
from langchain_core.outputs import ChatGeneration, ChatResult
from langchain_openai import ChatOpenAI
from sqlalchemy import inspect, select, update
from test_service import chat, create

from sky_ai.agent import OfflineChatModel
from sky_ai.database import Store, api_timestamp, messages, metadata, now, tool_calls
from sky_ai.intent import CustomerIntent, LlmIntentClassifier


def test_structured_intent_classifier_uses_argument_free_business_boundary():
    """验证第三层使用结构化 Function Calling，schema 不包含身份和业务参数。"""
    requests = []

    def model_server(request):
        body = json.loads(request.content)
        requests.append(body)
        function = body["tools"][0]["function"]
        properties = function["parameters"]["properties"]
        assert set(properties) == {"intent", "confidence"}
        assert "order_id" not in json.dumps(body["tools"])
        return httpx.Response(
            200,
            json={
                "id": "intent-123",
                "object": "chat.completion",
                "created": int(time.time()),
                "model": "qwen-plus",
                "choices": [
                    {
                        "index": 0,
                        "message": {
                            "role": "assistant",
                            "content": None,
                            "tool_calls": [
                                {
                                    "id": "intent-call-123",
                                    "type": "function",
                                    "function": {
                                        "name": function["name"],
                                        "arguments": json.dumps(
                                            {
                                                "intent": "SHOP_STATUS_QUERY",
                                                "confidence": "HIGH",
                                            }
                                        ),
                                    },
                                }
                            ],
                        },
                        "finish_reason": "tool_calls",
                    }
                ],
                "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15},
            },
        )

    with httpx.Client(transport=httpx.MockTransport(model_server)) as http:
        model = ChatOpenAI(
            model="qwen-plus",
            api_key="test-key",
            base_url="http://mock-model/v1",
            http_client=http,
            max_retries=0,
        )
        classifier = LlmIntentClassifier(model)
        decision = classifier.invoke("今天还接客吗", [], {})
    assert decision.intent == CustomerIntent.SHOP_STATUS_QUERY
    assert decision.confidence == "HIGH"
    assert len(requests) == 1


def test_openai_compatible_function_calling(env):
    """通过模拟模型服务验证 OpenAI-compatible 工具选择、业务执行和最终回答链路。"""
    requests = []

    def model_server(request):
        """模拟两轮模型 HTTP 响应，先请求门店工具，再依据工具消息返回最终回答。"""
        body = json.loads(request.content)
        requests.append(body)
        assert request.url.path == "/v1/chat/completions"
        assert "userId" not in json.dumps(body["tools"])
        if len(requests) == 1:
            message = {
                "role": "assistant",
                "content": None,
                "tool_calls": [
                    {
                        "id": "call-123",
                        "type": "function",
                        "function": {"name": "get_shop_status", "arguments": "{}"},
                    }
                ],
            }
            reason = "tool_calls"
        else:
            assert body["messages"][-1]["role"] == "tool"
            message = {"role": "assistant", "content": "门店正在营业。"}
            reason = "stop"
        return httpx.Response(
            200,
            json={
                "id": "chat-123",
                "object": "chat.completion",
                "created": int(time.time()),
                "model": "qwen-plus",
                "choices": [{"index": 0, "message": message, "finish_reason": reason}],
                "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15},
            },
        )

    with httpx.Client(transport=httpx.MockTransport(model_server)) as http:
        env[0].app.state.agent.model = ChatOpenAI(
            model="qwen-plus",
            api_key="test-key",
            base_url="http://mock-model/v1",
            http_client=http,
            max_retries=0,
        )
        response = chat(env, create(env), "门店营业吗")
    assert response.status_code == 200, response.text
    assert response.json()["answer"] == "门店正在营业。"
    assert response.json()["intent"] == "SHOP_STATUS"
    assert len(requests) == 2 and len(env[2]) == 1


class NoToolModel(OfflineChatModel):
    def _generate(self, messages, **kwargs):
        """返回未调用业务工具的模拟回答，用于验证模型跳过查询会被拦截。"""
        return ChatResult(generations=[ChatGeneration(message=AIMessage(content="我猜正在营业"))])


def test_model_cannot_skip_required_business_lookup(env):
    """验证明确业务问题在缺少成功工具结果时失败，不展示模型猜测。"""
    env[0].app.state.agent.model = NoToolModel()
    response = chat(env, create(env), "门店营业吗")
    assert response.status_code == 503
    assert response.json()["code"] == "TOOL_REQUIRED"
    assert "我猜" not in response.text


class RepeatedToolModel(OfflineChatModel):
    def _generate(self, messages, **kwargs):
        """持续请求同一门店工具，用于检验框架调用上限和工具结果复用。"""
        return ChatResult(
            generations=[
                ChatGeneration(
                    message=AIMessage(
                        content="",
                        tool_calls=[
                            {
                                "name": "get_shop_status",
                                "args": {},
                                "id": "repeat",
                                "type": "tool_call",
                            }
                        ],
                    )
                )
            ]
        )


def test_framework_bounds_model_loop_and_deduplicates_tools(env):
    """验证重复调用模型会被框架上限终止，且实际业务工具和审计只执行一次。"""
    env[0].app.state.agent.model = RepeatedToolModel()
    response = chat(env, create(env), "门店营业吗")
    assert response.status_code == 503
    assert len(env[2]) == 1
    with env[1].engine.connect() as conn:
        assert len(conn.execute(select(tool_calls)).all()) == 1


def test_alembic_adopts_existing_tables_without_losing_history(env, monkeypatch):
    """验证 Alembic 可重复接管已有 AI 表，并保留此前创建的会话历史。"""
    cid = create(env)
    monkeypatch.setenv("AI_DATABASE_URL", str(env[1].engine.url))
    config = Config(str(Path(__file__).parents[1] / "alembic.ini"))
    command.upgrade(config, "head")
    command.upgrade(config, "head")
    assert env[1].get(1, cid)["title"] == "测试"
    assert "alembic_version" in inspect(env[1].engine).get_table_names()


def test_alembic_creates_clean_database(tmp_path, monkeypatch):
    """验证空测试数据库可迁移出三张 AI 表及 Alembic 版本表。"""
    from sqlalchemy import create_engine

    url = f"sqlite:///{tmp_path / 'new.db'}"
    monkeypatch.setenv("AI_DATABASE_URL", url)
    command.upgrade(Config(str(Path(__file__).parents[1] / "alembic.ini")), "head")
    engine = create_engine(url)
    assert set(inspect(engine).get_table_names()) == {
        "ai_conversation",
        "ai_message",
        "ai_tool_call",
        "alembic_version",
    }
    message_columns = {column["name"] for column in inspect(engine).get_columns("ai_message")}
    assert {"processing_owner", "lease_expires_at"} <= message_columns
    engine.dispose()


def test_java_local_datetime_is_rendered_as_real_utc():
    """验证旧 Java 的 Asia/Shanghai LocalDateTime 不会被误标为 UTC。"""
    assert api_timestamp(datetime(2026, 9, 14, 20, 30, 15, 123000)) == (
        "2026-09-14T12:30:15.123Z"
    )


def test_alembic_upgrades_legacy_v2_pending_turn_with_recoverable_lease(
    tmp_path, monkeypatch
):
    """验证不含租约列的 Java V2 表可升级，遗留 PENDING 会被标记为已过期。"""
    url = f"sqlite:///{tmp_path / 'legacy-v2.db'}"
    store = Store(url)
    metadata.create_all(store.engine)
    cid = store.create(1, "旧会话")["conversation_id"]
    _, assistant, _ = store.begin_turn(
        1, cid, "未完成", "legacy-pending", "fake", "fake", "trace-123"
    )
    with store.engine.begin() as conn:
        conn.exec_driver_sql("DROP INDEX ix_ai_message_lease_expires_at")
        conn.exec_driver_sql("ALTER TABLE ai_message DROP COLUMN lease_expires_at")
        conn.exec_driver_sql("ALTER TABLE ai_message DROP COLUMN processing_owner")

    monkeypatch.setenv("AI_DATABASE_URL", url)
    command.upgrade(Config(str(Path(__file__).parents[1] / "alembic.ini")), "head")
    assert store.expire_stale_turns() == 1
    with store.engine.connect() as conn:
        row = conn.execute(
            select(messages.c.status, messages.c.error_code).where(
                messages.c.message_id == assistant["message_id"]
            )
        ).one()
        assert row == ("FAILED", "WORKER_LEASE_EXPIRED")
    store.engine.dispose()


def test_recovery_marks_only_interrupted_turns(env):
    """验证恢复操作只处理待完成轮次、可重复执行，且不会阻止后续新请求。"""
    from sky_ai.recover import recover_pending

    cid = create(env)
    assert chat(env, cid, "门店营业吗").status_code == 200
    store = env[1]
    _, assistant, _ = store.begin_turn(1, cid, "订单19", "interrupted", "fake", "fake", "trace-123")
    store.audit_start(cid, assistant["message_id"], "get_order_progress", {}, "trace-123")
    assert recover_pending(store) == 1
    assert recover_pending(store) == 0
    assert chat(env, cid, "门店营业吗", "new-request").status_code == 200
    with store.engine.connect() as conn:
        assert (
            conn.execute(
                select(tool_calls.c.result_status).where(
                    tool_calls.c.message_id == assistant["message_id"]
                )
            ).scalar_one()
            == "FAILED"
        )


def test_expired_lease_is_recovered_automatically_on_next_turn(env):
    """验证进程崩溃遗留的过期租约无需停机命令即可释放会话。"""
    cid = create(env)
    store = env[1]
    _, assistant, _ = store.begin_turn(
        1,
        cid,
        "订单19",
        "expired-request",
        "fake",
        "fake",
        "trace-123",
        "dead-worker",
        300,
    )
    tid = store.audit_start(
        cid, assistant["message_id"], "get_order_progress", {}, "trace-123"
    )
    with store.engine.begin() as conn:
        conn.execute(
            update(messages)
            .where(messages.c.message_id == assistant["message_id"])
            .values(lease_expires_at=now() - timedelta(seconds=1))
        )

    _, replacement, replayed = store.begin_turn(
        1,
        cid,
        "门店营业吗",
        "replacement-request",
        "fake",
        "fake",
        "trace-456",
        "healthy-worker",
        300,
    )
    assert not replayed
    assert replacement["status"] == "PENDING"
    with store.engine.connect() as conn:
        expired = conn.execute(
            select(messages.c.status, messages.c.error_code, messages.c.version).where(
                messages.c.message_id == assistant["message_id"]
            )
        ).one()
        assert expired == ("FAILED", "WORKER_LEASE_EXPIRED", 1)
        audit = conn.execute(
            select(tool_calls.c.result_status, tool_calls.c.result_code, tool_calls.c.version).where(
                tool_calls.c.tool_call_id == tid
            )
        ).one()
        assert audit == ("FAILED", "WORKER_LEASE_EXPIRED", 1)


def test_concurrent_duplicate_tool_calls_execute_once(env):
    """验证同轮并发执行同一工具时仅发出一次业务请求，并向所有调用方返回结果。"""
    from concurrent.futures import ThreadPoolExecutor

    cid = create(env)
    _, assistant, _ = env[1].begin_turn(1, cid, "门店营业吗", "race", "fake", "fake", "trace-123")
    tool = env[0].app.state.agent.business_tools.bind(
        "get_shop_status", {}, 1, cid, assistant["message_id"], "trace-123"
    )

    def invoke_tool(_):
        return tool.invoke({})

    with ThreadPoolExecutor(max_workers=4) as pool:
        results = list(pool.map(invoke_tool, range(4)))
    for result in results:
        assert result["status"] == "OPEN"
    assert len(env[2]) == 1
