# 外卖智能客服与个性化菜品推荐 Agent

基于“苍穹外卖”业务系统扩展的智能客服 Agent 项目。用户前端由微信小程序改为了web页面，并采用miniIO替代了阿里OSS。项目目标是让大模型在受控权限下查询实时业务数据、理解多轮上下文，并结合用户偏好完成可解释的菜品推荐。

> [!IMPORTANT]
> 当前仓库处于 **PoC 阶段**。门店状态、指定订单进度、会话持久化、三级意图路由雏形、双 JWT、Function Calling 和 SSE 基础链路已经落地；个性化推荐、RAG、Redis 会话上下文、人工转接等能力尚未实现。本文用 ✅、🟡、⬜ 明确标注完成度，避免将规划能力误认为现有功能。
>
> **欢迎Comment&Fork**

## 项目简介

传统客服系统通常依赖固定问答或关键词匹配，难以处理“这单到哪了”“换个便宜点的”一类带指代、省略和连续约束的表达。本项目在完整外卖业务系统之上新增独立 AI 服务，计划通过“规则识别 → 上下文解析 → LLM 决策”的三级路由连接订单、门店和菜品能力，同时将身份、资源范围和工具权限收敛在服务端。

当前版本已经能够：

- 验证用户 JWT，创建并查询属于当前用户的客服会话；
- 查询门店实时营业状态；
- 查询当前用户指定订单的进度，并在数据库层校验订单归属；
- 从显式参数或文本中提取订单 ID，拒绝多 ID、冲突 ID 和非法 ID；
- 保存用户消息、助手消息、调用耗时、错误码和脱敏后的工具审计记录；
- 使用 Fake Provider 离线开发，或通过 OpenAI-compatible 协议接入 Qwen；
- 通过 SSE 返回稳定事件 ID，并结合请求幂等键支持事件级回放。

## 实现状态

图例：✅ 已实现 · 🟡 部分实现 · ⬜ 未实现

| 模块 | 状态 | 当前实现与边界 |
| --- | :---: | --- |
| 外卖基础业务 | ✅ | 用户登录、菜单、购物车、地址、下单、订单、店铺管理及管理端等基础能力 |
| Web 用户端智能客服 | ✅ | Vue 3 客服页、会话切换、历史加载、SSE 消费、失败重试 |
| MySQL 会话持久化 | ✅ | `ai_conversation`、`ai_message`、`ai_tool_call`，由 Flyway 管理 |
| 多轮上下文 | 🟡 | 读取 MySQL 最近 12 条已完成消息，并维护一个已验证的关联订单；尚无摘要、偏好和多实体状态 |
| 三级意图路由 | 🟡 | 已有规则层、订单上下文层和 LLM 兜底；当前仅覆盖门店状态、订单进度、退款边界及通用问答 |
| Function Calling | 🟡 | 已注册 `get_shop_status`、`get_order_progress` 两个只读白名单工具 |
| 订单进度查询 | ✅ | `orderId + userId` 在业务查询层同时约束，越权订单与不存在订单统一返回 |
| 门店状态查询 | ✅ | 由 `sky-server` 读取 Redis；缺失或异常状态返回 `UNKNOWN` |
| 双 JWT 鉴权 | ✅ | 服务 JWT + 短期用户上下文 JWT；模型不能传入或改写 `userId` |
| 幂等与并发控制 | ✅ | 请求指纹、`clientRequestId`、唯一约束、会话行锁、消息/工具乐观锁 |
| SSE 流式响应 | 🟡 | 支持稳定事件 ID、`Last-Event-ID` 和幂等回放；当前为完整回答生成后的分块发送，并非模型原生 Token 流 |
| 调用审计与可观测性 | 🟡 | 已记录 Trace ID、模型元数据、耗时、状态和错误码；尚无指标看板、告警和完整安全事件体系 |
| Redis 热上下文 | ⬜ | 尚未缓存最近消息、会话摘要、用户偏好、焦点实体或幂等状态 |
| 滚动摘要与 Token 预算 | ⬜ | 尚未实现动态摘要、Token 计数和预算化上下文组装 |
| 菜品偏好槽位抽取 | ⬜ | 尚未抽取预算、辣度、口味、忌口、过敏原等结构化偏好 |
| 混合菜品推荐 | ⬜ | 尚未实现 SQL 硬过滤、RAG 语义召回、确定性重排和推荐解释 |
| Embedding / 向量库 | ⬜ | 尚未建立菜品向量索引、增量更新和召回评测 |
| 推荐结果回查 | ⬜ | 尚未在输出前重新验证价格、启售状态及约束条件 |
| 更多业务工具 | ⬜ | `search_menu`、`get_recent_orders`、`get_order_detail`、`create_handoff` 尚未实现 |
| 人工客服转接 | ⬜ | 尚未实现工单、优先级、会话接管状态机及管理端处理页面 |
| 限流、熔断与降级 | ⬜ | 已配置 HTTP 超时和安全错误模型，但尚无完整限流、熔断和自动降级策略 |

## 系统架构

```mermaid
flowchart LR
    U[用户] --> W[Vue 3 用户端]
    W -->|JWT / SSE| A[sky-ai-service<br/>Spring Boot 4 + Spring AI]
    A -->|会话、消息、工具审计| AM[(MySQL AI 专属表)]
    A -->|OpenAI-compatible| L[Fake Provider / Qwen]
    A -->|服务 JWT + 用户上下文 JWT| S[sky-server<br/>Spring Boot 2.7]
    S --> B[(MySQL 业务表)]
    S --> R[(Redis)]
    M[Vue 3 管理端] --> S
```

关键边界：

- `sky-ai-service` 只直接读写 AI 专属表，不直接查询订单等交易表，也不直接访问 Redis；
- 业务事实必须通过 `sky-server` 的内部白名单接口获得；
- 用户身份来自已验证的外部 JWT，工具 Schema 中不暴露 `userId`；
- 订单查询在 Mapper/Service 层同时限制订单 ID 和当前用户 ID；
- 模型只能看到本轮策略允许的工具，不能访问 SQL、任意 URL、密钥或内部实现。

## 核心链路

### 1. 三级意图路由

1. **规则层**：高精度识别门店状态、订单进度、退款表达和订单 ID。
2. **上下文层**：结合当前会话已验证的 `related_order_id` 和最近消息处理“这单”“它什么时候到”等指代。
3. **LLM 层**：前两层无法确定时交给模型，但最终仍由策略引擎决定可见工具。

退款属于未授权写操作，当前直接返回固定能力边界，不向模型开放退款工具。

### 2. 受控 Function Calling

当前仅开放两个只读工具：

| 工具 | 用途 | 安全约束 |
| --- | --- | --- |
| `get_shop_status` | 查询门店此刻是否营业 | 仅返回 `OPEN`、`CLOSED`、`UNKNOWN` |
| `get_order_progress` | 查询指定订单进度和允许操作 | 订单 ID 由服务端绑定，用户 ID 来自认证上下文 |

每次调用都会生成脱敏审计记录；同一轮重复选择相同工具只执行一次。

### 3. 会话、幂等与恢复

- MySQL 保存完整用户/助手消息以及工具调用状态；
- 会话行锁为消息分配严格递增的 `sequence_no`；
- `clientRequestId + SHA-256 请求指纹` 防止重复提交及幂等键被不同载荷复用；
- 助手消息通过 `reply_to_message_id` 与用户消息建立一对一回复关系；
- 消息和工具状态更新使用乐观锁；
- SSE 事件 ID 基于持久化消息 ID 生成，重连时复用 `clientRequestId` 并携带 `Last-Event-ID`。

## 技术栈

| 层次 | 当前技术 |
| --- | --- |
| AI 服务 | Java 21、Spring Boot 4.1、Spring AI 2.0、JDBC、Flyway |
| 业务服务 | Java、Spring Boot 2.7、MyBatis、MySQL、Redis、JWT、WebSocket |
| 模型接入 | Qwen OpenAI-compatible API、Function Calling、离线 Fake Provider |
| 用户端 | Vue 3、TypeScript、Vite、Pinia、Axios、Fetch SSE |
| 管理端 | Vue 3、TypeScript、Vite、Element Plus、ECharts |
| 测试 | JUnit 5、MockMvc、Mockito、Testcontainers、MySQL 8 |
| 规划引入 | RAG、Embedding、向量检索、Redis AI 上下文缓存 |

## 目录结构

```text
sky-take-out/
├─ sky-ai-service/              # 独立 AI 客服服务（Java 21，单独构建）
├─ sky-server/                  # 外卖业务服务与 AI 内部工具接口
├─ sky-common/                  # 公共常量、异常、工具类
├─ sky-pojo/                    # Entity / DTO / VO
├─ sky-web-user/                # Vue 3 用户端，包含智能客服页面
├─ project-sky-admin-vue-ts/    # Vue 3 管理端
├─ mp-weixin/                   # 微信小程序端
├─ docs/                        # Agent 设计、P0 规格、持久化设计
└─ sql/                         # 增量业务 SQL
```

> `sky-ai-service` 使用独立 POM，不在仓库根 Maven reactor 中，需要单独构建和启动。

## 本地运行

### 环境要求

- JDK 21（运行 AI 服务；也可用于构建当前业务服务）
- Maven 3.9+
- MySQL 8.0+
- Redis 6+
- Node.js 22+ 与 npm
- Docker（仅运行包含 Testcontainers 的 AI 集成测试时需要）

### 1. 准备数据与配置

先导入苍穹外卖基础数据库，并按需执行 `sql/` 中的增量脚本。业务服务的本地数据源、Redis、对象存储等配置位于 `sky-server/src/main/resources/application-dev.yml`。

开发配置中存在便于本地联调的示例凭据。公开部署前必须改为环境变量或密钥管理系统注入，并轮换所有已暴露的数据库、JWT、对象存储和第三方服务凭据。

AI 服务至少需要以下配置：

```powershell
$env:AI_DB_URL="jdbc:mysql://localhost:3306/sky_take_out?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true"
$env:AI_DB_USERNAME="root"
$env:AI_DB_PASSWORD="<your-password>"

# AI 服务：与 sky-server 的用户 JWT、内部服务 JWT 配置保持一致
$env:APP_AUTH_USER_JWT_SECRET="<user-jwt-secret>"
$env:APP_AUTH_SERVICE_JWT_SECRET="<service-secret-at-least-32-bytes>"
$env:APP_AUTH_USER_CONTEXT_JWT_SECRET="<different-context-secret-at-least-32-bytes>"

# sky-server 对应配置
$env:SKY_JWT_USER_SECRET_KEY=$env:APP_AUTH_USER_JWT_SECRET
$env:SKY_AI_INTERNAL_SERVICE_SECRET_KEY=$env:APP_AUTH_SERVICE_JWT_SECRET
$env:SKY_AI_INTERNAL_USER_CONTEXT_SECRET_KEY=$env:APP_AUTH_USER_CONTEXT_JWT_SECRET
```

如果 AI 表尚未由 Flyway 接管，首次连接已有非空业务库时临时设置：

```powershell
$env:AI_FLYWAY_BASELINE_ON_MIGRATE="true"
```

首次迁移成功后应移除该变量，避免在生产环境长期开启自动基线。

### 2. 启动业务服务

```powershell
mvn -pl sky-server -am spring-boot:run
```

默认端口为 `8080`。

### 3. 启动 AI 服务

默认使用不联网的 Fake Provider：

```powershell
mvn -f sky-ai-service/pom.xml spring-boot:run
```

切换到 Qwen：

```powershell
$env:SPRING_PROFILES_ACTIVE="qwen"
$env:AI_API_KEY="<your-api-key>"
$env:AI_CHAT_MODEL="qwen3.7-plus"
$env:AI_MODEL_BASE_URL="https://dashscope.aliyuncs.com/compatible-mode/v1"
mvn -f sky-ai-service/pom.xml spring-boot:run
```

AI 服务默认端口为 `8081`，健康检查地址为 `http://localhost:8081/actuator/health`。

### 4. 启动用户端

```powershell
Set-Location sky-web-user
npm install
npm run dev
```

用户端默认运行在 `http://localhost:5173`，开发代理将普通业务请求转发到 `8080`，将 AI 请求转发到 `8081`。

## API 概览

所有 AI 接口均通过 `authentication` 请求头接收现有用户 JWT。

```text
POST /api/ai/conversations
GET  /api/ai/conversations?limit=20&offset=0
GET  /api/ai/conversations/{conversationId}
GET  /api/ai/conversations/{conversationId}/messages?afterSequence=0&limit=50
POST /api/ai/conversations/{conversationId}/messages/stream
POST /api/ai/poc/chat
```

SSE 请求示例：

```http
POST /api/ai/conversations/{conversationId}/messages/stream
authentication: <user-jwt>
Content-Type: application/json
Accept: text/event-stream

{
  "message": "订单 101 到哪了？",
  "orderId": 101,
  "clientRequestId": "web-20260830-001"
}
```

事件类型包括 `message.delta`、`message.completed` 和 `error`。

## 测试

运行 AI 服务测试：

```powershell
mvn -f sky-ai-service/pom.xml test
```

当前测试覆盖 JWT、工具单次执行、Agent 策略、数据库迁移、用户归属、幂等、乐观锁、会话 API 和 SSE 回放。最近一次本地验证结果：**30 tests，0 failures，0 errors**。

业务服务测试：

```powershell
mvn -pl sky-server -am test
```

## 后续路线图（尚未实现）

- [ ] 新增 `search_menu`、`get_recent_orders`、`get_order_detail` 和 `create_handoff` 工具
- [ ] 使用 Redis 缓存最近消息、滚动摘要、结构化偏好、焦点订单集合和热幂等状态
- [ ] 基于 Token 预算动态选择最近消息、摘要、偏好、业务实体和检索片段
- [ ] 使用 LLM 抽取预算、辣度、口味、忌口和过敏原等偏好槽位
- [ ] 建立“SQL 硬约束过滤 → Embedding/RAG 召回 → 确定性重排”的推荐链路
- [ ] 在输出推荐前回查数据库，验证实时价格、启售状态和安全约束
- [ ] 支持“换个便宜点的”“不要辣”“和刚才不一样”等推荐追问
- [ ] 接入模型原生 Token 流，并持久化可跨实例恢复的 SSE 事件
- [ ] 实现人工转接、接管状态机、风险分级和管理端工单页面
- [ ] 增加限流、熔断、指标看板、告警、Prompt Injection 安全评测和离线推荐评测

详细设计参见：

- [`docs/ai-customer-service-agent-design.md`](docs/ai-customer-service-agent-design.md)
- [`docs/ai-customer-service-p0-spec.md`](docs/ai-customer-service-p0-spec.md)
- [`docs/ai-customer-service-p0-implementation-plan.md`](docs/ai-customer-service-p0-implementation-plan.md)
- [`docs/ai-customer-service-persistence-schema-design.md`](docs/ai-customer-service-persistence-schema-design.md)

## License

本项目采用 [Apache License 2.0](LICENSE)。
