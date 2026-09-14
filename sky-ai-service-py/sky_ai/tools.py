import time
from threading import Lock

import httpx
from langchain_core.tools import StructuredTool

from sky_ai.security import ApiError

PATHS = {
    "get_shop_status": ("GET", "/internal/ai-tools/shop/status"),
    "get_order_progress": ("GET", "/internal/ai-tools/users/me/orders/{orderId}/progress"),
    "recommend_dishes": ("POST", "/internal/ai-tools/catalog/dish-recommendations"),
    "recommend_meal_combo": ("POST", "/internal/ai-tools/catalog/meal-combinations"),
}
DESCRIPTIONS = {
    "get_shop_status": "查询真实门店营业状态。",
    "get_order_progress": "查询当前登录用户已指定的订单进度。订单编号已由服务端绑定。",
    "recommend_dishes": "按本轮已确认的预算、口味、过敏原偏好推荐可售菜品。",
    "recommend_meal_combo": "按本轮已确认的总预算、人数、口味和过敏原搭配整餐。",
}


class BusinessTools:
    def __init__(self, client, identity, store):
        """注入业务 HTTP 客户端、身份桥接器和审计存储，作为访问 Java 业务服务的出口。"""
        self.client = client
        self.identity = identity
        self.store = store

    def bind(self, name, params, user, cid, mid, trace):
        """为本轮请求构建 LangChain 工具，绑定名称、业务参数、用户、会话及审计信息。

        返回的工具不向模型暴露参数字段，身份和资源范围通过闭包固定。
        """
        cached = []
        lock = Lock()

        def execute() -> dict:
            """调用绑定的 Java 只读接口并记录审计，成功后缓存业务数据，失败时抛出安全错误。"""
            if cached:
                return cached[0]
            # Only bounded field names/order ID enter the audit, never raw user text or JWTs.
            summary = {"fields": sorted(params)}
            if "orderId" in params:
                summary["orderId"] = params["orderId"]
            tid = self.store.audit_start(cid, mid, name, summary, trace)
            started = time.monotonic()
            try:
                method, path = PATHS[name]
                request_path = path.format_map(params)
                headers = self.identity.headers(user, cid, trace)
                if method == "POST":
                    response = self.client.request(
                        method, request_path, headers=headers, json=params
                    )
                else:
                    response = self.client.request(method, request_path, headers=headers)
                if response.status_code == 404 and name == "get_order_progress":
                    raise ApiError(404, "ORDER_NOT_FOUND", "未找到当前账号下的该订单")
                if response.status_code in {401, 403}:
                    raise ApiError(503, "INTERNAL_AUTH_FAILED", "客服内部鉴权失败")
                if response.status_code == 400:
                    raise ApiError(400, "INVALID_RECOMMENDATION_PREFERENCES", "推荐条件不正确")
                response.raise_for_status()
                body = response.json()
                if (
                    not isinstance(body, dict)
                    or body.get("success") is not True
                    or not isinstance(body.get("data"), dict)
                ):
                    raise ApiError(503, "TOOL_INVALID_RESPONSE", "业务信息暂时无法查询")
                result = body["data"]
                cached.append(result)
            except Exception as exc:
                if isinstance(exc, ApiError):
                    error = exc
                else:
                    error = ApiError(503, "TOOL_UNAVAILABLE", "业务信息暂时无法查询")
                if isinstance(exc, httpx.TimeoutException):
                    status = "TIMEOUT"
                else:
                    status = "FAILED"
                updated = self.store.audit_finish(
                    tid,
                    status,
                    error.code,
                    int((time.monotonic() - started) * 1000),
                )
                if not updated:
                    raise ApiError(409, "TURN_LEASE_LOST", "本次请求已过期，请重新发送")
                raise error from None
            updated = self.store.audit_finish(
                tid, "SUCCEEDED", "OK", int((time.monotonic() - started) * 1000)
            )
            if not updated:
                raise ApiError(409, "TURN_LEASE_LOST", "本次请求已过期，请重新发送")
            if name == "get_order_progress":
                self.store.link_order(user, cid, params["orderId"])
            return result

        def execute_once() -> dict:
            # LangGraph may execute multiple tool calls from one model response concurrently.
            """在互斥锁内执行工具，并为同轮并发重复调用复用已缓存的成功结果。"""
            with lock:
                return execute()

        return StructuredTool.from_function(execute_once, name=name, description=DESCRIPTIONS[name])
