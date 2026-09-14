import time
from uuid import uuid4

import jwt

from sky_ai.config import Settings


class ApiError(Exception):
    def __init__(self, status: int, code: str, message: str):
        """保存 HTTP 状态码、稳定错误码和可安全展示给用户的错误消息。"""
        super().__init__(message)
        self.status = status
        self.code = code
        self.message = message


class IdentityBridge:
    def __init__(self, settings: Settings):
        """保存鉴权配置，供用户验签和内部双令牌签发使用。"""
        self.settings = settings

    def verify_user(self, token: str | None) -> int:
        """验证用户 JWT 的 HS256 签名和有效期，返回可信的正整数用户 ID。

        支持裸 JWT 和 Bearer 前缀；未配置密钥或认证失败时抛出 ApiError。
        """
        secret = self.settings.user_secret.get_secret_value()
        if not secret:
            raise ApiError(503, "AUTH_NOT_CONFIGURED", "用户鉴权尚未配置")
        if not token:
            token = ""
        if token.lower().startswith("bearer "):
            token = token[7:].strip()
        try:
            claims = jwt.decode(
                token, secret.encode(), algorithms=["HS256"], options={"require": ["exp", "userId"]}
            )
            user_id = claims["userId"]
            if type(user_id) is not int or not 0 < user_id <= 2**63 - 1:
                raise ValueError("Invalid user")
            return user_id
        except (jwt.PyJWTError, ValueError, TypeError):
            raise ApiError(401, "UNAUTHENTICATED", "登录状态无效或已过期") from None

    def headers(self, user_id: int, conversation_id: str, trace_id: str) -> dict:
        """为指定用户和会话签发短时双 JWT，返回调用 Java 工具接口所需的请求头。

        user_id 必须来自认证结果；两把内部密钥必须不同且至少为 32 字节。
        """
        service_key = self.settings.service_secret.get_secret_value()
        context_key = self.settings.context_secret.get_secret_value()
        if (
            len(service_key.encode()) < 32
            or len(context_key.encode()) < 32
            or service_key == context_key
        ):
            raise ApiError(503, "AUTH_NOT_CONFIGURED", "内部鉴权需要两把不同的至少32字节密钥")
        now = int(time.time())
        common = {"aud": "sky-server", "iat": now}
        service_claims = common.copy()
        service_claims["sub"] = "sky-ai-service"
        service_claims["tokenType"] = "service"
        service_claims["exp"] = now + 300
        service_claims["jti"] = str(uuid4())
        service = jwt.encode(
            service_claims,
            service_key,
            algorithm="HS256",
        )
        context_claims = common.copy()
        context_claims["tokenType"] = "user_context"
        context_claims["userId"] = user_id
        context_claims["conversationId"] = conversation_id
        context_claims["exp"] = now + 120
        context_claims["jti"] = str(uuid4())
        context = jwt.encode(
            context_claims,
            context_key,
            algorithm="HS256",
        )
        return {
            "Authorization": f"Bearer {service}",
            "X-AI-User-Context": context,
            "X-Trace-Id": trace_id,
        }
