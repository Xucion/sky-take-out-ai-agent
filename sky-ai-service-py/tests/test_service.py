import json
import time
from concurrent.futures import ThreadPoolExecutor
from decimal import Decimal

import jwt
import pytest
from sqlalchemy import select, update

from sky_ai.config import Settings
from sky_ai.database import conversations, messages, tool_calls
from sky_ai.intent import CustomerIntent, IntentDecision
from sky_ai.preferences import extract_preferences
from sky_ai.security import ApiError, IdentityBridge


def create(env):
    """通过测试客户端创建会话并检查响应，返回新会话 ID。"""
    client, _, _, auth, *_ = env
    response = client.post("/api/ai/conversations", json={"title": "测试"}, headers=auth())
    assert response.status_code == 201, response.text
    return response.json()["conversationId"]


def chat(env, cid, text, key="req-1", user=1):
    """携带指定用户身份和请求键调用普通聊天接口，返回原始测试响应。"""
    client, _, _, auth, *_ = env
    return client.post(
        "/api/ai/poc/chat",
        headers=auth(user),
        json={"conversationId": cid, "message": text, "clientRequestId": key},
    )


def test_shop_tool_replay_and_audit(env):
    """验证门店工具成功调用、请求回放、幂等冲突及脱敏审计记录。"""
    cid = create(env)
    first = chat(env, cid, "门店营业吗？")
    assert first.status_code == 200, first.text
    assert first.json()["toolUsed"] == "get_shop_status"
    replay = chat(env, cid, "门店营业吗？").json()
    assert replay["replayed"] is True
    assert replay["assistantMessageId"] == first.json()["assistantMessageId"]
    assert len(env[2]) == 1
    conflict = chat(env, cid, "开门吗？")
    assert conflict.status_code == 409
    with env[1].engine.connect() as conn:
        audit = conn.execute(select(tool_calls)).mappings().one()
        assert audit["result_status"] == "SUCCEEDED"
        assert "authentication" not in json.dumps(audit["parameter_summary"])


def test_auth_and_ownership(env):
    """验证未认证、过期或非法用户声明被拒绝，跨用户访问不会调用业务工具。"""
    cid = create(env)
    client, _, calls, auth, *_ = env
    assert client.get(f"/api/ai/conversations/{cid}").status_code == 401
    assert client.get(f"/api/ai/conversations/{cid}", headers=auth(2)).status_code == 404
    assert chat(env, cid, "订单19", user=2).status_code == 404
    assert not calls
    for claims in (
        {"userId": 1},
        {"userId": True, "exp": time.time() + 60},
        {"userId": 1, "exp": time.time() - 60},
    ):
        token = jwt.encode(claims, env[5].user_secret.get_secret_value(), algorithm="HS256")
        assert (
            client.get("/api/ai/conversations", headers={"authentication": token}).status_code
            == 401
        )


def test_order_clarification_followup_and_isolation(env):
    """验证缺少订单号时先追问、下一轮补号可查询，且关联订单不会泄漏到新会话。"""
    cid = create(env)
    assert "订单 ID" in chat(env, cid, "查一下订单进度").json()["answer"]
    assert len(env[2]) == 0
    second = chat(env, cid, "19", "req-2")
    assert second.status_code == 200, second.text
    assert "配送中" in second.json()["answer"]
    assert "/orders/19/" in env[2][-1].url.path
    assert chat(env, cid, "它到哪了", "req-3").status_code == 200
    other = create(env)
    assert "订单 ID" in chat(env, other, "它到哪了").json()["answer"]
    assert len(env[2]) == 2


@pytest.mark.parametrize(
    "text,code",
    [
        ("订单19和订单20", "AMBIGUOUS_ORDER_ID"),
        ("订单0", "INVALID_ORDER_ID"),
        ("订单99999999999999999999", "INVALID_ORDER_ID"),
    ],
)
def test_order_guard(env, text, code):
    """验证多个订单号、非正数及超范围编号在调用业务工具前被拒绝。"""
    result = chat(env, create(env), text)
    assert result.status_code == 400, result.text
    assert result.json()["code"] == code
    assert not env[2]


def test_refund_is_not_a_tool(env):
    """验证退款请求返回能力边界说明，且不会调用业务工具。"""
    response = chat(env, create(env), "订单19帮我退款")
    assert response.status_code == 200
    assert "暂不支持" in response.json()["answer"]
    assert not env[2]


def test_semantic_fallback_routes_paraphrases_to_safe_actions(env):
    """验证规则未覆盖的自然表达由第三层分类，并且仍只开放对应的安全工具。"""
    shop = chat(env, create(env), "今天还接客吗")
    assert shop.status_code == 200, shop.text
    assert shop.json()["intent"] == "SHOP_STATUS"
    assert shop.json()["toolUsed"] == "get_shop_status"

    recommendation = chat(env, create(env), "有啥好吃的")
    assert recommendation.status_code == 200, recommendation.text
    assert recommendation.json()["intent"] == "DISH_RECOMMENDATION"
    assert recommendation.json()["toolUsed"] == "recommend_dishes"

    unsupported = chat(env, create(env), "这个钱能原路回来吗")
    assert unsupported.status_code == 200, unsupported.text
    assert unsupported.json()["intent"] == "UNSUPPORTED_WRITE"
    assert unsupported.json()["toolUsed"] is None
    assert "暂不支持" in unsupported.json()["answer"]


def test_semantic_order_intent_still_requires_server_validated_order_id(env):
    """验证第三层只能识别订单意图，不能生成订单号或绕过确定性的槽位校验。"""
    cid = create(env)
    first = chat(env, cid, "外卖小哥走到什么地方了")
    assert first.status_code == 200, first.text
    assert first.json()["intent"] == "ORDER_PROGRESS"
    assert "订单 ID" in first.json()["answer"]
    assert not env[2]

    second = chat(env, cid, "19", "req-2")
    assert second.status_code == 200, second.text
    assert second.json()["toolUsed"] == "get_order_progress"
    assert "/orders/19/" in env[2][-1].url.path


def test_semantic_classifier_failure_does_not_expose_business_tools(env):
    """验证第三层超时或解析失败时降级为无工具通用回答，不扩大业务权限。"""

    class BrokenClassifier:
        def invoke(self, text, history, context):
            raise RuntimeError("untrusted provider details")

    env[0].app.state.agent.intent_classifier = BrokenClassifier()
    response = chat(env, create(env), "一种规则没有覆盖的新说法")
    assert response.status_code == 200, response.text
    assert response.json()["intent"] == "GENERAL"
    assert response.json()["toolUsed"] is None
    assert not env[2]


def test_ambiguous_semantic_intent_uses_fixed_clarification(env):
    """验证分类为多意图时返回服务端固定追问，不执行模型提供的动作。"""

    class AmbiguousClassifier:
        def invoke(self, text, history, context):
            return IntentDecision(intent=CustomerIntent.AMBIGUOUS, confidence="LOW")

    env[0].app.state.agent.intent_classifier = AmbiguousClassifier()
    response = chat(env, create(env), "帮我处理一下这些事情")
    assert response.status_code == 200, response.text
    assert response.json()["intent"] == "GENERAL"
    assert "请明确一下" in response.json()["answer"]
    assert response.json()["toolUsed"] is None
    assert not env[2]


@pytest.mark.parametrize("oid,status", [(404, 404), (500, 503)])
def test_tool_failure_persisted_without_order_link(env, oid, status):
    """验证业务工具失败落库、内部错误脱敏，且失败查询不更新会话订单关联。"""
    cid = create(env)
    response = chat(env, cid, f"订单{oid}")
    assert response.status_code == status, response.text
    assert "sensitive" not in response.text
    with env[1].engine.connect() as conn:
        assert conn.execute(select(tool_calls.c.result_status)).scalar_one() == "FAILED"
        assert (
            conn.execute(
                select(messages.c.status).where(messages.c.role == "ASSISTANT")
            ).scalar_one()
            == "FAILED"
        )
    assert env[1].get(1, cid)["related_order_id"] is None
    assert chat(env, cid, f"订单{oid}").status_code == 503
    assert len(env[2]) == 1


def test_recommendation_multiturn(env):
    """验证预算追问、中文人数解析，以及多轮口味更新时保留过敏原约束。"""
    cid = create(env)
    first = chat(env, cid, "推荐菜，预算80，我对花生过敏")
    assert first.status_code == 200, first.text
    assert "总预算" in first.json()["answer"]
    assert not env[2]
    second = chat(env, cid, "两个人吃", "req-2")
    assert second.status_code == 200, second.text
    assert second.json()["toolUsed"] == "recommend_meal_combo"
    body = json.loads(env[2][-1].content)
    assert body["totalBudget"] == 80 and body["peopleCount"] == 2
    assert body["allergens"] == ["花生"]
    chat(env, cid, "改为微辣，不要甜", "req-3")
    body = json.loads(env[2][-1].content)
    assert body["allergens"] == ["花生"] and body["spicyLevelMin"] == 1
    assert body["sweetnessLevelMax"] == 0


def test_history_pagination(env):
    """验证普通聊天产生的历史消息支持游标分页。"""
    cid = create(env)
    client, _, _, auth, *_ = env
    response = chat(env, cid, "门店营业吗")
    assert response.status_code == 200, response.text
    page = client.get(f"/api/ai/conversations/{cid}/messages?limit=1", headers=auth()).json()
    assert page["hasMore"] and page["nextAfterSequence"] == 1
    page2 = client.get(
        f"/api/ai/conversations/{cid}/messages?afterSequence=1", headers=auth()
    ).json()
    assert page2["items"][0]["role"] == "ASSISTANT"


def test_request_validation_and_inactive_conversation(env):
    """验证空白消息、非法请求键及人工接管会话无法进入自动聊天流程。"""
    cid = create(env)
    assert chat(env, cid, "  ").status_code == 400
    assert chat(env, cid, "你好", "invalid key").status_code == 400
    with env[1].engine.begin() as conn:
        conn.execute(update(conversations).values(current_handler="HUMAN"))
    assert chat(env, cid, "你好").status_code == 409


def test_atomic_same_conversation_claim(env):
    """验证同一会话并发提交时仅一轮成功占用，且消息序号不会重复分配。"""
    cid = create(env)
    store = env[1]

    def claim(i):
        """并发尝试占用第 i 个测试请求，返回占用结果或已知业务错误码。"""
        try:
            return store.begin_turn(1, cid, "你好", f"req-{i}", "fake", "fake", "trace-123")
        except ApiError as exc:
            return exc.code

    with ThreadPoolExecutor(max_workers=4) as pool:
        results = list(pool.map(claim, range(4)))
    successful_claims = 0
    for item in results:
        if isinstance(item, tuple):
            successful_claims += 1
    assert successful_claims == 1
    assert results.count("REQUEST_IN_PROGRESS") == 3
    assert store.get(1, cid)["last_message_sequence"] == 2


def test_tools_have_no_model_controlled_arguments(env):
    """验证工具 schema 不暴露可由模型填写的身份、订单号或推荐参数字段。"""
    tool = env[0].app.state.agent.business_tools.bind(
        "get_order_progress", {"orderId": 19}, 1, "cid", "mid", "trace"
    )
    assert tool.args_schema.model_json_schema()["properties"] == {}


def test_preference_business_rules():
    """验证单菜预算、口味和过敏原提取，以及人均预算到整餐预算的换算。"""
    prefs = extract_preferences({}, "想吃微辣的，30元以内，要下饭，不要甜，我对花生过敏")
    name, body = prefs.tool_request()
    assert name == "recommend_dishes" and body["maxPrice"] == 30
    assert body["allergens"] == ["花生"] and body["preferredTags"] == ["下饭"]
    prefs = extract_preferences({}, "两人吃，人均40元")
    assert prefs.tool_request()[1]["totalBudget"] == 80
    fractional = extract_preferences({}, "三人吃，人均19.90元")
    assert fractional.maxPrice * fractional.peopleCount == Decimal("59.70")
    assert fractional.tool_request()[1]["totalBudget"] == 59.7


def test_message_and_tool_terminal_updates_use_version_cas(env):
    """验证错误版本、错误处理者和重复终态更新都不能覆盖已持久化状态。"""
    cid = create(env)
    store = env[1]
    _, assistant, _ = store.begin_turn(
        1, cid, "你好", "cas-message", "fake", "fake", "trace-123", "worker-a"
    )
    assert not store.finish(
        assistant["message_id"], "错误版本", assistant["version"] + 1, "worker-a"
    )
    assert not store.finish(
        assistant["message_id"], "错误处理者", assistant["version"], "worker-b"
    )
    assert not store.renew_lease(assistant["message_id"], "worker-b", 300)
    assert store.renew_lease(assistant["message_id"], "worker-a", 300)
    assert store.finish(
        assistant["message_id"], "完成", assistant["version"], "worker-a"
    )
    assert not store.finish(
        assistant["message_id"], "重复覆盖", assistant["version"], "worker-a"
    )

    tid = store.audit_start(cid, assistant["message_id"], "get_shop_status", {}, "trace-123")
    assert not store.audit_finish(tid, "SUCCEEDED", "OK", 1, expected_version=1)
    assert store.audit_finish(tid, "SUCCEEDED", "OK", 1, expected_version=0)
    assert not store.audit_finish(tid, "FAILED", "LATE", 2, expected_version=0)

    with store.engine.connect() as conn:
        row = conn.execute(
            select(messages.c.content, messages.c.status, messages.c.version).where(
                messages.c.message_id == assistant["message_id"]
            )
        ).one()
        assert row == ("完成", "COMPLETED", 1)
        audit = conn.execute(
            select(tool_calls.c.result_status, tool_calls.c.result_code, tool_calls.c.version).where(
                tool_calls.c.tool_call_id == tid
            )
        ).one()
        assert audit == ("SUCCEEDED", "OK", 1)


def test_internal_secrets_must_be_distinct():
    """验证服务令牌和用户上下文令牌不能使用相同密钥签发。"""
    bridge = IdentityBridge(
        Settings(
            _env_file=None,
            APP_AUTH_SERVICE_JWT_SECRET="x" * 32,
            APP_AUTH_USER_CONTEXT_JWT_SECRET="x" * 32,
        )
    )
    with pytest.raises(ApiError):
        bridge.headers(1, "cid", "trace-123")
