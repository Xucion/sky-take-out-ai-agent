import time

import fakeredis
import httpx
import jwt
import pytest
from fastapi.testclient import TestClient

from sky_ai.config import Settings
from sky_ai.database import Store, metadata
from sky_ai.main import create_app


@pytest.fixture
def env(tmp_path):
    """创建临时数据库、内存 Redis 和模拟业务客户端，提供测试应用及鉴权辅助函数。"""
    settings = Settings(
        _env_file=None,
        AI_DATABASE_URL=f"sqlite:///{tmp_path / 'test.db'}",
        APP_AUTH_USER_JWT_SECRET="user-secret-for-tests-1234567890123456",
        APP_AUTH_SERVICE_JWT_SECRET="service-secret-for-tests-1234567890123456",
        APP_AUTH_USER_CONTEXT_JWT_SECRET="context-secret-for-tests-1234567890123456",
    )
    store = Store(settings.sqlalchemy_url())
    metadata.create_all(store.engine)
    calls = []

    def business(request):
        """模拟 Java 工具接口，验证双 JWT，并按请求返回业务成功或指定故障响应。"""
        calls.append(request)
        service = jwt.decode(
            request.headers["Authorization"][7:],
            settings.service_secret.get_secret_value(),
            algorithms=["HS256"],
            audience="sky-server",
        )
        context = jwt.decode(
            request.headers["X-AI-User-Context"],
            settings.context_secret.get_secret_value(),
            algorithms=["HS256"],
            audience="sky-server",
        )
        assert service["sub"] == "sky-ai-service" and service["tokenType"] == "service"
        assert context["userId"] == 1 and context["tokenType"] == "user_context"
        if "/orders/404/" in request.url.path:
            return httpx.Response(404)
        if "/orders/500/" in request.url.path:
            return httpx.Response(500, text="sensitive upstream details")
        if request.url.path.endswith("/status"):
            data = {"status": "OPEN", "statusText": "营业中"}
        elif "/orders/" in request.url.path:
            data = {"statusText": "配送中", "orderId": 19}
        else:
            data = {"items": [{"dishId": 1, "name": "宫保鸡丁", "price": 25}], "emptyReason": None}
        return httpx.Response(200, json={"success": True, "data": data})

    http = httpx.Client(base_url="http://business", transport=httpx.MockTransport(business))
    cache = fakeredis.FakeRedis(decode_responses=True)
    app = create_app(settings, store, cache, http)
    with TestClient(app) as client:

        def auth(user=1):
            """为指定测试用户签发一小时有效的 JWT，返回 authentication 请求头。"""
            return {
                "authentication": jwt.encode(
                    {"userId": user, "exp": time.time() + 3600},
                    settings.user_secret.get_secret_value(),
                    algorithm="HS256",
                )
            }

        yield client, store, calls, auth, cache, settings
