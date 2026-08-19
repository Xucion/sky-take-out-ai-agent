# sky-ai-service

独立的智能客服 PoC 服务。它有自己的 Maven 构建和 Java 21 / Spring Boot / Spring AI
版本，不属于根项目的 Maven reactor，也不访问业务数据库或 Redis。

## 当前能力

- 验证现有用户 JWT，并从可信 claims 中提取 `userId`；
- 为每次工具调用签发服务 JWT 和短时用户上下文 JWT；
- 通过 `sky-server` 查询门店状态和当前用户的一笔订单进度；
- 默认使用不联网的 Fake Provider；
- 可通过 OpenAI-compatible 配置切换 Qwen；
- 只提供 P0 JSON PoC 接口，尚未包含会话持久化、SSE 和人工工单。

## 本地运行

先启动 `sky-server`，并为两个服务设置一致的三个密钥：

```powershell
$env:SKY_USER_JWT_SECRET="与 sky-server 用户 JWT 一致的密钥"
$env:SKY_AI_SERVICE_SECRET_KEY="至少 32 字节的内部服务密钥"
$env:SKY_AI_USER_CONTEXT_SECRET_KEY="至少 32 字节且不同于上面的上下文密钥"
mvn -f sky-ai-service/pom.xml spring-boot:run
```

默认 `AI_PROVIDER=fake`，不会调用外部模型。请求示例：

```http
POST http://localhost:8081/api/ai/poc/chat
authentication: <现有用户 JWT>
Content-Type: application/json

{"conversationId":"local-001","message":"门店现在营业吗？"}
```

订单查询在 body 中额外传入 `orderId`。服务端不会接收或相信客户端提供的 `userId`。

## 切换 Qwen

```powershell
$env:SPRING_PROFILES_ACTIVE="qwen"
$env:AI_API_KEY="..."
$env:AI_CHAT_MODEL="qwen-plus"
$env:AI_MODEL_BASE_URL="https://dashscope.aliyuncs.com/compatible-mode/v1"
mvn -f sky-ai-service/pom.xml spring-boot:run
```

生产环境应由密钥管理系统注入配置，并在发布前轮换示例环境使用过的密钥。
