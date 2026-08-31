# sky-ai-service

独立的智能客服 PoC 服务。它有自己的 Maven 构建和 Java 21 / Spring Boot / Spring AI
版本，不属于根项目的 Maven reactor。服务通过独立数据源访问 AI 专属表，不直接读取
订单等交易业务表；会话级菜品推荐偏好保存在 Redis，并设置短期 TTL。

## 当前能力

- 验证现有用户 JWT，并从可信 claims 中提取 `userId`；
- 为每次工具调用签发服务 JWT 和短时用户上下文 JWT；
- 通过 `sky-server` 查询门店状态和当前用户的一笔订单进度；
- 通过结构化偏好调用 `sky-server` 的确定性菜品筛选、排序和多人整餐组合服务；
- 通过 Flyway 管理会话、消息和工具调用审计三张 AI 专属表；
- 提供带用户归属约束、消息幂等与工具审计状态流转的 JDBC Repository；
- 提供创建会话、分页查询本人会话、查询本人会话详情和游标分页查询历史消息的 API；
- 默认使用不联网的 Fake Provider；
- 可通过 OpenAI-compatible 配置切换 Qwen；
- Qwen 通过 Function Calling 自主选择四个只读工具，Java 层绑定认证身份、订单 ID 和推荐条件；
- `/api/ai/poc/chat` 已持久化用户/助手消息和脱敏工具审计，并支持请求幂等回放；
- 提供带稳定事件 ID、幂等回放和 `Last-Event-ID` 续传的最小 SSE 消息接口；
- 已组装最近 12 条已完成消息和受控业务上下文；尚未包含模型原生 Token 流、滚动摘要、Token 预算、反馈和人工工单。

## 代码结构

生产代码按业务功能组织，避免 Controller、Service、Repository 全局分层后相互交叉引用：

```text
com.sky.ai
├─ SkyAiServiceApplication       # Spring Boot 启动入口
├─ common                       # 配置、异常和双 JWT 身份桥接
├─ conversation                 # 会话 API、应用服务与持久化
├─ chat                         # 普通聊天、SSE 协议和接口模型
└─ agent                        # Agent 编排、意图、推荐上下文、策略、Provider 和业务工具
```

同一功能内高度相关的小型请求、响应和事件统一放在 `ConversationModels`、`ChatModels`、
`ChatStreamEvent`、`PolicyTypes` 和 `ToolModels` 中，独立业务组件仍保持单一职责类。

## 本地运行

先启动 MySQL、Redis 和 `sky-server`，并配置 AI 数据源及两个服务一致的三个密钥：

```powershell
$env:AI_DB_URL="jdbc:mysql://localhost:3306/sky_take_out?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true"
$env:AI_DB_USERNAME="root"
$env:AI_DB_PASSWORD="本地数据库密码"
$env:AI_REDIS_HOST="localhost"
$env:AI_REDIS_PORT="6379"
$env:APP_AUTH_USER_JWT_SECRET="与 sky-server 用户 JWT 一致的密钥"
$env:APP_AUTH_SERVICE_JWT_SECRET="至少 32 字节的内部服务密钥"
$env:APP_AUTH_USER_CONTEXT_JWT_SECRET="至少 32 字节且不同于上面的上下文密钥"
mvn -f sky-ai-service/pom.xml spring-boot:run
```

对已有的非空 `sky_take_out` 数据库，首次启用 Flyway 时临时设置：

```powershell
$env:AI_FLYWAY_BASELINE_ON_MIGRATE="true"
mvn -f sky-ai-service/pom.xml spring-boot:run
Remove-Item Env:AI_FLYWAY_BASELINE_ON_MIGRATE
```

该设置会以版本 `0` 建立基线并继续执行 `V1`。确认 `flyway_schema_history`
记录成功后应关闭开关，不能在生产环境长期默认开启。

默认 `AI_PROVIDER=fake`，不会调用外部模型。请求示例：

```http
POST http://localhost:8081/api/ai/poc/chat
authentication: <现有用户 JWT>
Content-Type: application/json

{"conversationId":"local-001","message":"门店现在营业吗？","clientRequestId":"web-001"}
```

订单查询不接收独立的订单 ID 字段；订单号只从对话文本或当前会话的受控上下文中取得。
服务端也不会接收或相信客户端提供的 `userId`。

## Function Calling 安全边界

每次请求只动态注册当前意图需要的 `get_shop_status`、`get_order_progress`、
`recommend_dishes` 或 `recommend_meal_combo`。工具都不接收模型生成的用户 ID；订单 ID 由规则层或当前会话上下文解析并锁定，
推荐工具参数由应用维护的结构化会话偏好生成。工具调用仍经过双内部 JWT、业务归属校验和 `ai_tool_call` 审计。
同一轮重复选择同一工具只执行一次。

订单 ID 可从“订单id为19”“订单号19”“19号订单”等明确文本中确定性提取。首次询问订单进度但
未提供编号时，服务端会在 Redis 中记录待补充状态；用户下一轮只回复“19”也能继续查询。
同一条消息包含多个订单 ID 时，请求会在调用模型和业务工具前被拒绝。

## 三层意图管线

请求先经过高精度规则层，识别门店查询、订单查询、菜品推荐、退款和明确订单 ID；未完成的订单意图再由
上下文层结合当前用户、当前会话的 `related_order_id` 和最近 12 条已完成消息解析；前两层无法
确定时才进入 Qwen + Function Calling。所有分支最终都经过 `PolicyEngine`，并由
`CapabilityRegistry` 决定本轮模型实际能看到的工具。

订单工具成功并通过业务归属校验后，才更新当前会话的订单槽位。下一轮“它到哪了”等追问可
复用该订单；新会话和其他用户不能继承。退款目前由规则层和策略层返回固定能力边界，不调用
Qwen、不注册工具，也不产生工具调用审计。

## 菜品推荐 MVP

首次启用前，在业务数据库执行 [dish-recommendation-migration.sql](../sql/dish-recommendation-migration.sql)，
然后通过管理端菜品新增/编辑页配置辣度、甜度、咸度、油腻程度、推荐标签和过敏原。没有配置
画像的菜品不会进入推荐候选，避免在过敏原数据未知时作出不安全结论。

推荐会从“30 元以内”“微辣”“不要甜”“下饭”“对花生过敏”等表达中提取白名单字段，写入
`ai:ctx:{conversationId}:recommendation`，默认 TTL 为 30 分钟。多轮消息可以覆盖已有条件；
“预算 50”但未说明单菜预算或总预算时会先追问。业务服务依次执行起售、预算、分类、排除标签、
最高甜度和过敏原硬过滤，再按辣度、偏好标签、价格及分类多样性排序，最多返回 5 项及稳定原因码。

推荐请求示例：

```json
{"conversationId":"local-001","message":"想吃点微辣的，30元以内，要下饭，不要甜，我对花生过敏","clientRequestId":"recommend-001"}
```

## 会话 API

以下接口都从 `authentication` 请求头验证当前用户 JWT；不存在与跨用户会话统一返回
`404 / CONVERSATION_NOT_FOUND`：

```text
POST /api/ai/conversations
GET  /api/ai/conversations?limit=20&offset=0
GET  /api/ai/conversations/{conversationId}
GET  /api/ai/conversations/{conversationId}/messages?afterSequence=0&limit=50
```

创建会话的请求体只允许可选的 `title`。历史消息按 `sequenceNo` 正序返回，下一页使用响应中的
`nextAfterSequence`。`/api/ai/poc/chat` 成功或失败后都可以通过该接口查询对应消息状态。

`clientRequestId` 是同一会话内的请求幂等键，只允许字母、数字、点、下划线、冒号和短横线。
相同键与相同请求会直接回放已完成回复，不再次调用模型或工具；相同键复用不同消息
会返回 `IDEMPOTENCY_CONFLICT`。

## SSE 消息接口

```http
POST /api/ai/conversations/{conversationId}/messages/stream
authentication: <现有用户 JWT>
X-Trace-Id: optional-trace-id
Last-Event-ID: optional-previous-event-id
Content-Type: application/json
Accept: text/event-stream

{"message":"门店现在营业吗？","clientRequestId":"web-sse-001"}
```

当前事件为 `message.delta`、`message.completed` 和 `error`。事件 ID 基于已持久化的助手
`messageId` 生成；客户端重连时应复用同一 `clientRequestId` 并携带最后处理的
`Last-Event-ID`，服务端会回放尚未确认的事件且不会重复创建消息或调用工具。

浏览器端需要使用支持 POST 和自定义请求头的 `fetch` 流读取方式，不能直接使用只支持 GET 的
原生 `EventSource`。当前 Fake/Qwen Provider 接口仍是一次性返回完整文本，因此首版 SSE 会在
回答生成完成后按 Unicode 字符安全分块；这不是模型原生 Token 流。

## 切换 Qwen

```powershell
$env:SPRING_PROFILES_ACTIVE="qwen"
$env:AI_API_KEY="..."
$env:AI_CHAT_MODEL="qwen-plus"
$env:AI_MODEL_BASE_URL="https://dashscope.aliyuncs.com/compatible-mode/v1"
mvn -f sky-ai-service/pom.xml spring-boot:run
```

未显式设置 `SPRING_PROFILES_ACTIVE=qwen` 时始终使用 Fake Provider。生产环境应由密钥管理
系统注入配置，任何曾写入配置文件、终端日志或聊天记录的 API Key 都必须在控制台轮换，
不能只从代码中删除。
