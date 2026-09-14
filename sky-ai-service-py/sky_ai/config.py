from typing import Literal

from pydantic import Field, SecretStr, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict
from sqlalchemy import URL


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    provider: Literal["fake", "qwen", "openai"] = Field("fake", alias="AI_PROVIDER")
    port: int = Field(8081, ge=1, le=65535, alias="AI_SERVICE_PORT")
    api_key: SecretStr = Field(SecretStr(""), alias="AI_API_KEY")
    model_name: str = Field("qwen-plus", alias="AI_CHAT_MODEL")
    model_base_url: str = Field(
        "https://dashscope.aliyuncs.com/compatible-mode/v1", alias="AI_MODEL_BASE_URL"
    )
    model_timeout: float = Field(45, gt=0, alias="AI_MODEL_TIMEOUT_SECONDS")
    database_url: str | None = Field(None, alias="AI_DATABASE_URL")
    db_host: str = Field("localhost", alias="AI_DB_HOST")
    db_port: int = Field(3306, alias="AI_DB_PORT")
    db_name: str = Field("sky_take_out", alias="AI_DB_NAME")
    db_username: str = Field("root", alias="AI_DB_USERNAME")
    db_password: SecretStr = Field(SecretStr(""), alias="AI_DB_PASSWORD")
    redis_url: str = Field("redis://localhost:6379/1", alias="AI_REDIS_URL")
    context_ttl: int = Field(1800, gt=0, alias="AI_CONTEXT_TTL_SECONDS")
    turn_lease_seconds: int = Field(300, ge=30, alias="AI_TURN_LEASE_SECONDS")
    sky_server_base_url: str = Field("http://localhost:8080", alias="SKY_SERVER_BASE_URL")
    tool_timeout: float = Field(5, gt=0, alias="SKY_SERVER_TIMEOUT_SECONDS")
    user_secret: SecretStr = Field(SecretStr(""), alias="APP_AUTH_USER_JWT_SECRET")
    service_secret: SecretStr = Field(SecretStr(""), alias="APP_AUTH_SERVICE_JWT_SECRET")
    context_secret: SecretStr = Field(SecretStr(""), alias="APP_AUTH_USER_CONTEXT_JWT_SECRET")

    @model_validator(mode="after")
    def validate_model(self):
        """校验真实模型提供方已配置 API Key，并返回校验后的设置对象。"""
        if self.provider != "fake" and not self.api_key.get_secret_value():
            raise ValueError("AI_API_KEY is required for a real model provider")
        return self

    def sqlalchemy_url(self):
        """优先返回显式配置的数据库 URL，否则使用分项参数构造 MySQL 连接地址。"""
        if self.database_url:
            return self.database_url
        return URL.create(
            "mysql+pymysql",
            username=self.db_username,
            password=self.db_password.get_secret_value(),
            host=self.db_host,
            port=self.db_port,
            database=self.db_name,
            query={"charset": "utf8mb4"},
        )
