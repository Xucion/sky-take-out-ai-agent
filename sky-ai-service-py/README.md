# sky-ai-service-py

将原 `sky-ai-service` 的 AI 服务迁移为 Python 独立进程。业务仍由 `sky-server`
负责；前端通过 8081 端口的 `/api/ai/...` 接口访问，聊天使用普通 JSON 请求和响应。

## 框架与职责

| 功能 | 实现 |
| --- | --- |
| HTTP、请求校验、依赖注入 | FastAPI + Pydantic |
| 业务路由、澄清、Agent 节点 | LangGraph `StateGraph` |
| 第三层语义意图识别 | `ChatOpenAI.with_structured_output`，仅输出受限意图枚举 |
| 模型选择工具、执行工具、继续生成回答 | LangChain `create_agent`，不手写 ReAct 循环 |
| Qwen / OpenAI-compatible 模型客户端 | `langchain-openai.ChatOpenAI` |
| 工具参数 schema | LangChain `StructuredTool` |
| 模型和工具调用上限 | LangChain `ModelCallLimitMiddleware` / `ToolCallLimitMiddleware` |
| JWT 签发及验签 | PyJWT，固定 HS256 |
| AI 表访问与迁移 | SQLAlchemy + Alembic，兼容原 MySQL V1/V2 表 |
| 短期订单澄清、推荐偏好 | redis-py，用户 + 会话键，30 分钟 TTL |

订单号提取、预算口径澄清、过敏原约束、身份绑定、业务审计和请求幂等属于本项目的
业务规则，仍由应用控制。没有重新实现模型 SDK、JWT 算法或工具调度器。

意图按三层处理：高精度规则先识别明确表达，上下文层处理待补订单号、关联订单和
推荐偏好；前两层仍为未知时才调用结构化 LLM 分类器。分类结果只包含意图和置信度，
订单号、预算、人数、过敏原和工具参数仍由 Python 确定性解析和校验。分类失败时按无工具
通用问答安全降级，不会扩大模型可见的业务能力。

## 本地启动（PowerShell）

在本目录执行；使用已创建的 `.venv`，不需要重新建虚拟环境：

```powershell
.\.venv\Scripts\python.exe -m pip install -r requirements.lock
.\.venv\Scripts\python.exe -m pip install -e . --no-deps
Copy-Item .env.example .env
```

填写 `.env` 的数据库密码和三个 JWT 密钥：

- `APP_AUTH_USER_JWT_SECRET`：与 Java 用户登录 JWT 的原始字符串密钥一致。
- `APP_AUTH_SERVICE_JWT_SECRET`、`APP_AUTH_USER_CONTEXT_JWT_SECRET`：与
  `sky-server` 内部 AI 鉴权配置一致，两者必须不同且至少 32 字节。
- `AI_REDIS_URL`：例如 `redis://localhost:6379/1`；有密码时使用 Redis URL。
- `AI_TURN_LEASE_SECONDS`：处理中消息的租约，默认 300 秒；运行期间自动续租。
- `AI_DB_HOST/PORT/NAME/USERNAME/PASSWORD`：MySQL 参数。
  也可设置 SQLAlchemy 格式的 `AI_DATABASE_URL`。不接受 JDBC URL。

环境变量优先于 `.env`；不读取旧服务 YAML 中的硬编码密钥。`.env` 已被 Git 忽略。

启动 MySQL、Redis 和 Java `sky-server`，然后执行：

```powershell
.\.venv\Scripts\python.exe -m alembic upgrade head
.\.venv\Scripts\python.exe -m sky_ai
```

- 默认地址：`http://127.0.0.1:8081`，可通过 `AI_SERVICE_PORT` 修改端口。
- Swagger：`http://127.0.0.1:8081/docs`。
- 健康检查：`GET /actuator/health`，检查数据库连接和 Redis。
- 不要同时让旧 Java AI 服务和 Python AI 服务接收同一批会话请求。
- 部署时可用 `python -m uvicorn sky_ai.main:create_app --factory --host 0.0.0.0 --port 8081`。

默认 `AI_PROVIDER=fake` 不调用外部模型，但查询、推荐仍会真实调用 Java 业务服务。
Fake 是一个 LangChain ChatModel，使用同一套 Agent 工具循环，业务数据不会伪造。
本地 Fake 另有确定性的语义分类夹具，用于在没有 API Key 时验证第三层路由。

## 切换 Qwen

编辑 `.env` 后重启：

```dotenv
AI_PROVIDER=qwen
AI_API_KEY=填入模型密钥
AI_CHAT_MODEL=qwen-plus
AI_MODEL_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
```

也支持 `AI_PROVIDER=openai` 搭配相应的模型名、密钥和兼容端点。Python 服务本身不加载模型权重。

## 保留的接口与能力

```text
POST /api/ai/conversations
GET  /api/ai/conversations?limit=20&offset=0
GET  /api/ai/conversations/{conversationId}
GET  /api/ai/conversations/{conversationId}/messages?afterSequence=0&limit=20
POST /api/ai/poc/chat
```

认证使用原 `authentication` 请求头，支持裸 JWT 或 Bearer JWT；请求体不允许传 `userId`。
聊天请求使用 `message`、`clientRequestId` 和 `conversationId`。
会话必须先通过创建接口产生。跨用户访问统一返回 404。

四个只读工具：门店状态、订单进度、菜品推荐、整餐组合。模型看到的工具参数为空：
身份、订单号和推荐条件由服务端绑定，Java 继续检查订单归属和执行菜品筛选、排序。
工具错误中断回答，不能让模型自行编造结果。同轮重复工具调用复用结果。
退款、下单、支付和取消订单不在工具清单内。

推荐支持预算澄清、中文/数字人数、多轮口味和过敏原保留。人均预算 × 人数作为
整餐总预算，修正了旧实现将人均预算等同于单菜预算的语义。

## 持久化与切换

- 沿用 `ai_conversation`、`ai_message`、`ai_tool_call`，不访问交易业务表。
- 已有 Java Flyway V1/V2：Alembic 检查必要字段后建立自己的版本记录，不删除历史。
- 空 MySQL：执行随代码保存的原始 V1/V2 SQL，保留默认值、索引、毫秒精度和大小写敏感键。
- 只有 V1 或部分 AI 表：迁移拒绝接管，先完成旧 V2/修复原迁移。
- Python 不改写 `flyway_schema_history`。回退旧服务时停止 Python，并遵循旧服务的 Flyway 配置。
- Redis 使用 `ai:py:ctx:{userId}:{conversationId}`。旧 Java Redis 热偏好不自动导入；
  持久化历史和已验证订单关联保留，切换后尚未完成的预算/订单澄清可能需要再次补充。
- SQL 消息历史是会话记录的唯一来源，最近 12 条已完成消息传给模型。
  LangGraph 每轮独立运行，不额外启用第二份消息 checkpointer，避免和 HTTP 幂等历史分叉。
- 数据库行锁与唯一约束防止并发请求重复执行；同会话上一轮未完成时返回 409。
  已完成请求可回放，相同请求键复用不同正文返回 `IDEMPOTENCY_CONFLICT`。
- 消息和工具终态使用 `version` 条件做乐观锁 CAS；处理中消息带 Worker 所有者和
  可续租截止时间。进程异常后，租约到期会在服务启动或下一次会话请求时自动标记失败，
  不会永久阻塞会话。
- 为兼容旧 Java 服务的 `LocalDateTime`，MySQL `DATETIME` 统一按 Asia/Shanghai
  无时区值存储；连接建立时固定为 `+08:00`，HTTP 响应再明确转换为 UTC `Z` 时间。
- 推荐金额在 Python 内部使用 `Decimal` 提取和计算，转换为 JSON 数值前不经过浮点运算。

正常情况下，进程异常留下的 `PENDING` 会在租约到期后自动恢复。紧急运维时也可以停止
所有 AI 服务进程后，显式把尚未到期的处理中请求标记失败：

```powershell
.\.venv\Scripts\python.exe -m sky_ai.recover --workers-stopped
```

恢复将中断消息和未完成审计标记失败，客户端用新 `clientRequestId` 重试。

聊天通过 `POST /api/ai/poc/chat` 一次返回完整 JSON 回答。用户端等待期间显示加载状态，
收到响应后展示完整回答；网络中断后复用 `clientRequestId` 可回放已完成结果。
已移除完整回答生成后分块发送的 SSE 接口和事件续传逻辑。

## 验证

```powershell
.\.venv\Scripts\python.exe -m pytest -q
.\.venv\Scripts\ruff.exe check sky_ai migrations tests
```

测试使用临时 SQLite、fakeredis、HTTP Mock，覆盖真实 LangGraph/LangChain 执行、
OpenAI-compatible Function Calling 协议、鉴权隔离、并发占用、幂等、历史分页、
订单澄清、推荐偏好、工具失败和数据库迁移。无需 API Key，不访问真实业务服务。
依赖锁定文件由现有 Windows / Python 3.14 虚拟环境生成。

当前没有真实 Qwen、MySQL 或 Java 服务端到端验证；部署前应按上述配置联调。
RAG、人工转接、滚动摘要与模型原生 Token 流与旧版一样尚未实现。

框架参考：[LangChain Agents](https://docs.langchain.com/oss/python/langchain/agents)、
[LangGraph](https://docs.langchain.com/oss/python/langgraph/overview)、
[内置中间件](https://docs.langchain.com/oss/python/langchain/middleware/built-in)。
