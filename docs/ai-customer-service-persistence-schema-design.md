# AI 客服会话持久化表设计

## 1. 文档目的

本文档定义 AI 客服第一阶段的三张核心持久化表：

- `ai_conversation`：保存会话归属、状态和消息排序游标；
- `ai_message`：保存用户可见的用户消息与助手消息；
- `ai_tool_call`：保存经过脱敏的工具调用审计信息。

对应 Flyway 迁移脚本为 `sky-ai-service/src/main/resources/db/migration/V1__create_ai_customer_service_tables.sql`。当前脚本面向 MySQL 8.0+，三张表已在本地 `sky_take_out` 数据库完成建表和冒烟验证。

本阶段只建立会话、消息和工具审计能力，不包含人工工单、用户反馈、知识库或模型评测表。

## 2. 设计目标

表结构需要满足以下目标：

1. 会话必须归属于通过 JWT 认证的当前用户；
2. 服务重启后仍可读取完整的用户可见历史；
3. 同一会话内的消息具有稳定且严格递增的顺序；
4. 重复提交同一请求时能够避免重复保存用户消息；
5. 每次工具调用可以按会话、消息和 Trace ID 追踪；
6. 工具审计不保存 JWT、模型密钥、完整敏感参数或完整业务响应；
7. AI 数据与交易数据隔离，AI 服务不得通过这些表直接读取订单业务数据；
8. 为后续 SSE、人工接管、故障恢复和历史分页保留扩展空间。

## 3. 数据关系

```text
ai_conversation 1 ────── N ai_message
       │                       │
       │                       └──── 1 ────── N ai_tool_call
       │
       └──────────────────────────── 1 ────── N ai_tool_call
```

- `ai_conversation.conversation_id` 是对外稳定会话 ID；
- `ai_message.conversation_id` 外键关联会话；
- `ai_tool_call.conversation_id` 外键关联会话；
- `ai_tool_call.message_id` 可选外键关联触发工具调用的助手消息；
- 三张 AI 表之间使用外键保持审计链完整；
- `user_id` 和 `related_order_id` 不关联 `user`、`orders` 外键，避免 AI 服务与交易表结构耦合。

删除会话时不能依靠级联删除。未来实现数据保留或用户数据删除策略时，需要在明确的事务中依次处理工具审计、消息和会话，避免误删审计数据。

## 4. ID 与字符集策略

每张表使用自增 `BIGINT id` 作为内部主键，降低聚簇索引和表间查询成本。客户端不可使用内部主键。

对外 ID 单独保存：

- `conversation_id`：对外会话 ID；
- `message_id`：对外消息 ID；
- `tool_call_id`：工具调用 ID。

这些 ID 由 `sky-ai-service` 生成，最大长度为 64，使用 `ascii_bin` 排序规则，按大小写精确比较。实现时可以使用 UUID、ULID 或等价的高熵不可预测 ID，不能使用连续数据库主键充当对外 ID。

表默认字符集为 `utf8mb4`，排序规则为 `utf8mb4_unicode_ci`，用于保存中文会话标题和消息正文。

## 5. `ai_conversation` 会话表

### 5.1 职责

`ai_conversation` 是用户隔离和会话状态控制的根表。所有会话读取、继续对话、关闭会话等操作都必须同时使用：

```sql
conversation_id = ? AND user_id = 当前认证用户ID
```

只按 `conversation_id` 查询后再在 Java 中判断用户归属是不允许的。不存在和不属于当前用户的会话应对外返回相同的“未找到”结果。

### 5.2 字段

| 字段 | 类型 | 空值/默认值 | 说明 |
| --- | --- | --- | --- |
| `id` | `BIGINT` | 主键、自增 | 内部主键，不对客户端暴露 |
| `conversation_id` | `VARCHAR(64)` | 非空、唯一 | 对外会话 ID，由 AI 服务生成 |
| `user_id` | `BIGINT` | 非空 | 会话所属用户，只能来自认证上下文 |
| `channel` | `VARCHAR(32)` | 非空，默认 `WEB` | 会话渠道，为后续多端接入预留 |
| `title` | `VARCHAR(100)` | 可空 | 用户可见会话标题 |
| `status` | `VARCHAR(32)` | 非空，默认 `BOT_ACTIVE` | 会话状态 |
| `current_handler` | `VARCHAR(16)` | 非空，默认 `BOT` | 当前回复控制方 |
| `related_order_id` | `BIGINT` | 可空 | 可选关联订单，只作引用，不建立交易表外键 |
| `last_message_sequence` | `BIGINT` | 非空，默认 `0` | 已分配的最后消息序号 |
| `last_message_time` | `DATETIME(3)` | 可空 | 最后一条消息时间，用于会话列表排序 |
| `started_at` | `DATETIME(3)` | 当前时间 | 会话开始时间 |
| `ended_at` | `DATETIME(3)` | 可空 | 会话结束时间 |
| `version` | `BIGINT` | 非空，默认 `0` | 乐观锁版本号 |
| `create_time` | `DATETIME(3)` | 当前时间 | 创建时间 |
| `update_time` | `DATETIME(3)` | 自动更新 | 最后更新时间 |

### 5.3 状态值

`status` 当前预留以下稳定值：

| 状态 | 含义 |
| --- | --- |
| `BOT_ACTIVE` | Bot 可以正常回复 |
| `WAITING_HUMAN` | 已申请人工，等待接管 |
| `HUMAN_ACTIVE` | 人工已接管，Bot 禁止回复 |
| `RESOLVED` | 问题已解决 |
| `CLOSED` | 会话已关闭 |

`current_handler` 当前预留：

| 处理方 | 含义 |
| --- | --- |
| `BOT` | Bot 拥有回复权 |
| `HUMAN` | 人工拥有回复权 |
| `NONE` | 当前没有回复方 |

状态流转必须由服务端状态机控制，不能由模型输出直接修改。

### 5.4 索引

| 索引 | 字段 | 用途 |
| --- | --- | --- |
| `uk_ai_conversation_conversation_id` | `conversation_id` | 对外 ID 唯一定位 |
| `idx_ai_conversation_user_updated` | `user_id, update_time, id` | 分页查询当前用户的最近会话 |
| `idx_ai_conversation_status_updated` | `status, update_time, id` | 按状态查询待处理会话 |
| `idx_ai_conversation_related_order` | `related_order_id` | 按关联订单定位会话 |

## 6. `ai_message` 消息表

### 6.1 职责

`ai_message` 保存完整的用户可见消息历史。发送给模型的上下文窗口可以裁剪或摘要，但不能用模型上下文窗口替代数据库中的历史记录。

用户消息应在调用模型前保存。助手消息可以先以 `PENDING` 状态创建，生成成功后更新为 `COMPLETED`；模型或工具失败时更新为 `FAILED` 并记录稳定错误码，不能保存异常堆栈。

### 6.2 字段

| 字段 | 类型 | 空值/默认值 | 说明 |
| --- | --- | --- | --- |
| `id` | `BIGINT` | 主键、自增 | 内部主键 |
| `message_id` | `VARCHAR(64)` | 非空、唯一 | 对外消息 ID |
| `conversation_id` | `VARCHAR(64)` | 非空、外键 | 所属对外会话 ID |
| `sequence_no` | `BIGINT` | 非空 | 会话内严格递增序号 |
| `role` | `VARCHAR(16)` | 非空 | 消息角色 |
| `content` | `MEDIUMTEXT` | 非空 | 用户可见消息正文 |
| `status` | `VARCHAR(24)` | 非空，默认 `PENDING` | 消息处理状态 |
| `client_request_id` | `VARCHAR(64)` | 可空 | 客户端请求幂等键，同一会话内唯一 |
| `request_fingerprint` | `VARCHAR(64)` | 可空 | 用户请求的 SHA-256 指纹，用于拒绝幂等键复用不同载荷 |
| `reply_to_message_id` | `VARCHAR(64)` | 可空、唯一、自外键 | 助手消息对应的用户消息 ID |
| `provider` | `VARCHAR(32)` | 可空 | 模型提供方，用户消息为空 |
| `model_name` | `VARCHAR(100)` | 可空 | 模型名称或版本，用户消息为空 |
| `input_tokens` | `INT UNSIGNED` | 可空 | 输入 Token 数 |
| `output_tokens` | `INT UNSIGNED` | 可空 | 输出 Token 数 |
| `latency_ms` | `INT UNSIGNED` | 可空 | 生成完整消息的耗时 |
| `error_code` | `VARCHAR(64)` | 可空 | 稳定、安全的错误码 |
| `trace_id` | `VARCHAR(64)` | 可空 | 链路追踪 ID |
| `version` | `BIGINT` | 非空，默认 `0` | 乐观锁版本号 |
| `create_time` | `DATETIME(3)` | 当前时间 | 创建时间 |
| `update_time` | `DATETIME(3)` | 自动更新 | 最后更新时间 |

### 6.3 角色与状态

`role` 当前预留：

- `USER`：用户消息；
- `ASSISTANT`：AI 助手消息；
- `SYSTEM`：系统生成且需要持久展示的消息；
- `TOOL`：需要作为用户可见历史保存的工具消息。

普通工具原始响应不应写入 `content`，只应在 `ai_tool_call` 中保存脱敏审计摘要。

`status` 当前预留：

- `PENDING`：消息已占位，仍在处理；
- `COMPLETED`：消息生成并保存完成；
- `FAILED`：处理失败；
- `CANCELLED`：生成任务被取消，例如人工接管后丢弃待发送回复。

### 6.4 消息排序与并发

`conversation_id + sequence_no` 具有唯一约束。分配新消息序号时，应在同一数据库事务中原子递增 `ai_conversation.last_message_sequence`，并使用得到的新值写入消息。

推荐流程：

```text
开始事务
  → 按 conversation_id + user_id 锁定或条件更新会话
  → last_message_sequence = last_message_sequence + 1
  → 取得新 sequence_no
  → 插入 ai_message
  → 更新 last_message_time
提交事务
```

不能使用“先查询最大序号再加一”的方式，否则并发请求可能获得相同序号。

### 6.5 幂等约束与索引

| 索引/约束 | 字段 | 用途 |
| --- | --- | --- |
| `uk_ai_message_message_id` | `message_id` | 对外消息 ID 唯一定位 |
| `uk_ai_message_conversation_sequence` | `conversation_id, sequence_no` | 保证会话内消息顺序唯一 |
| `uk_ai_message_conversation_request` | `conversation_id, client_request_id` | 防止同一请求重复保存消息 |
| `uk_ai_message_reply_to_message` | `reply_to_message_id` | 保证一条用户消息最多对应一条助手回复 |
| `idx_ai_message_conversation_created` | `conversation_id, create_time, id` | 会话历史分页和辅助排序 |
| `idx_ai_message_trace` | `trace_id` | 按 Trace ID 排查请求 |

`client_request_id` 和 `request_fingerprint` 写在用户消息上；助手消息通过 `reply_to_message_id` 关联用户消息。相同幂等键只有在请求指纹一致时才能回放，避免客户端误用同一键查询不同订单。`V2__link_assistant_replies_to_user_messages.sql` 以向后兼容方式增加这两个字段、自外键和唯一约束。

## 7. `ai_tool_call` 工具调用审计表

### 7.1 职责

`ai_tool_call` 记录工具调用的可观测信息，用于安全审计、故障定位和后续质量评测。该表不是业务响应缓存，不允许保存完整订单、用户地址、手机号、JWT、内部服务令牌、模型 API Key 或异常堆栈。

工具调用开始前先插入 `STARTED` 记录；完成、失败、超时或被拒绝后，再更新最终状态、结果码和耗时。这样即使调用进程异常退出，也可以发现长期停留在 `STARTED` 的不完整调用。

### 7.2 字段

| 字段 | 类型 | 空值/默认值 | 说明 |
| --- | --- | --- | --- |
| `id` | `BIGINT` | 主键、自增 | 内部主键 |
| `tool_call_id` | `VARCHAR(64)` | 非空、唯一 | 工具调用 ID |
| `conversation_id` | `VARCHAR(64)` | 非空、外键 | 所属对外会话 ID |
| `message_id` | `VARCHAR(64)` | 可空、外键 | 触发调用的助手消息 ID |
| `tool_name` | `VARCHAR(100)` | 非空 | 白名单工具名称 |
| `parameter_summary` | `JSON` | 可空 | 脱敏后的参数摘要 |
| `result_status` | `VARCHAR(24)` | 非空，默认 `STARTED` | 工具调用状态 |
| `result_code` | `VARCHAR(64)` | 可空 | 稳定业务结果码或错误码 |
| `result_summary` | `VARCHAR(1000)` | 可空 | 脱敏、截断后的结果摘要 |
| `latency_ms` | `INT UNSIGNED` | 可空 | 工具调用耗时 |
| `idempotency_key` | `VARCHAR(128)` | 可空、唯一 | 未来写工具使用的幂等键 |
| `trace_id` | `VARCHAR(64)` | 非空 | 链路追踪 ID |
| `version` | `BIGINT` | 非空，默认 `0` | 乐观锁版本号 |
| `create_time` | `DATETIME(3)` | 当前时间 | 调用开始时间 |
| `update_time` | `DATETIME(3)` | 自动更新 | 最后更新时间 |

### 7.3 状态值

| 状态 | 含义 |
| --- | --- |
| `STARTED` | 已记录调用，尚未获得最终结果 |
| `SUCCEEDED` | 工具调用成功 |
| `FAILED` | 工具或依赖返回失败 |
| `TIMEOUT` | 工具调用超时 |
| `REJECTED` | 参数、权限、安全规则或调用次数限制拒绝了调用 |

`result_code` 使用稳定代码，例如 `OK`、`ORDER_NOT_FOUND`、`INVALID_ARGUMENT` 或 `INTERNAL_ERROR`。禁止把 SQL 错误、内部 URL、异常消息或堆栈写入该字段。

### 7.4 脱敏规则

`parameter_summary` 和 `result_summary` 必须在写库前完成白名单化、脱敏和长度限制：

- `get_shop_status`：参数摘要可保存空 JSON；结果只保存 `OPEN/CLOSED/UNKNOWN` 等状态；
- `get_order_progress`：参数可保存订单 ID，但不得保存用户完整手机号、地址、订单备注或支付信息；
- 用户 ID 不从模型参数获取，可不写入参数摘要，通过会话归属追踪；
- 不保存用户 JWT、服务 JWT、短期用户上下文 JWT 或请求头；
- 不保存完整工具响应，只保存回答和排障必需的最小字段；
- 自由文本写入前应移除密钥、验证码、电话、地址等敏感内容，并截断到字段上限。

### 7.5 索引

| 索引/约束 | 字段 | 用途 |
| --- | --- | --- |
| `uk_ai_tool_call_tool_call_id` | `tool_call_id` | 工具调用 ID 唯一定位 |
| `uk_ai_tool_call_idempotency` | `idempotency_key` | 防止未来写工具重复执行 |
| `idx_ai_tool_call_conversation_created` | `conversation_id, create_time, id` | 按会话查看工具轨迹 |
| `idx_ai_tool_call_message` | `message_id` | 查看某条助手消息触发的调用 |
| `idx_ai_tool_call_tool_status_created` | `tool_name, result_status, create_time` | 工具质量和错误统计 |
| `idx_ai_tool_call_trace` | `trace_id` | 跨服务链路排障 |

## 8. 事务边界建议

一次普通聊天请求至少包含以下持久化阶段：

1. 验证用户 JWT；
2. 按 `conversation_id + user_id` 校验会话归属；
3. 在短事务中分配序号并保存用户消息；
4. 创建 `PENDING` 助手消息；
5. 提交事务后调用模型和业务工具，避免数据库事务覆盖网络调用时间；
6. 每个工具调用单独保存 `STARTED` 并更新最终状态；
7. 生成完成后更新助手消息及 Token、模型、耗时信息；
8. 失败时将助手消息更新为 `FAILED`，保留安全错误码。

模型和业务工具调用不能放在持有会话行锁的事务中，否则慢模型会放大数据库锁等待。

## 9. 会话归属查询规则

所有用户侧会话接口都应把用户归属放入 SQL 条件：

```sql
SELECT ...
FROM ai_conversation
WHERE conversation_id = :conversationId
  AND user_id = :authenticatedUserId;
```

读取历史消息时，应先用上述条件确认会话属于当前用户，再按 `conversation_id` 查询消息。也可以使用联表查询一次完成归属约束。禁止信任请求体、查询参数或模型输出中的 `userId`。

跨用户访问、随机会话 ID 和真实不存在的会话，对外应返回相同的 `CONVERSATION_NOT_FOUND`，不能暴露会话是否存在或属于谁。

## 10. 数据保留与审计边界

当前表结构保留完整用户可见消息，但尚未实现自动清理策略。正式上线前需要明确：

- 会话和消息保留期限；
- 用户注销或删除数据时的处理流程；
- 审计数据的最小保留期限；
- 哪些管理角色可以读取会话正文；
- 查询历史和导出数据的访问审计；
- 备份中的数据删除和过期策略。

应用日志默认不应重复记录完整消息正文、Prompt、工具参数或工具结果。数据库中允许保存的内容不代表可以无条件写入日志和监控平台。

## 11. 当前未覆盖范围

以下能力不属于这三张表的当前实现范围：

- `ai_handoff` 人工工单；
- `ai_feedback` 用户反馈；
- SSE 事件持久化和断点续传游标；
- 会话摘要和长期记忆；
- RAG 文档、版本、片段及引用；
- 模型评测运行和安全事件表；
- 数据保留任务及管理端审计日志。

后续增加这些能力时应使用独立迁移文件，不直接修改已经执行过的迁移脚本。

## 12. 验证记录

当前 DDL 已完成以下验证：

- 在 MySQL 8.0.45 的 `sky_take_out` 数据库成功创建三张表；
- 外键、唯一约束和索引创建成功；
- 事务内成功插入一条会话、两条消息和一次工具调用；
- 测试事务回滚后，三张表均为空；
- 重复执行迁移脚本只产生“表已存在”提示，不重复写入数据；
- 未修改 `user`、`orders` 或其他交易业务表。

## 13. Repository 实现状态

三张表的 JDBC Repository 已位于 `sky-ai-service/src/main/java/com/sky/ai/persistence/`：

- `ConversationRepository`：创建会话、按 `conversation_id + user_id` 查询归属、分页查询本人会话；
- `MessageRepository`：锁定本人会话后原子分配消息序号、按 `client_request_id` 幂等保存、分页读取本人历史、使用版本号更新助手消息终态；
- `ToolCallRepository`：创建 `STARTED` 审计、校验关联消息归属、按版本号执行一次终态更新、按会话读取本人工具轨迹。

当前实现遵循以下约束：

- 不存在和跨用户会话统一返回 `CONVERSATION_NOT_FOUND`；
- 消息序号在持有会话行锁的短事务内分配，不使用 `MAX(sequence_no) + 1`；
- 同一会话的相同 `client_request_id` 返回原消息，不重复插入；
- 助手消息只能从 `PENDING` 更新到终态，且必须匹配乐观锁版本；
- 工具调用只能从 `STARTED` 更新一次终态，跨用户更新返回失败；
- 工具参数摘要由 `ObjectMapper` 生成合法 JSON，并拒绝 JWT、Token、密码、用户 ID、电话和地址等敏感键；
- Repository 不查询 `user`、`orders` 等交易业务表。

MySQL Testcontainers 已覆盖迁移幂等、会话隔离、消息幂等、6 路并发消息排序、助手消息乐观锁、工具审计状态流转和敏感参数键拒绝。当前已经提供四个会话接口；`/api/ai/poc/chat` 也已接入用户消息、助手消息和工具审计持久化。真实 JWT + MySQL HTTP 集成测试覆盖成功落库、相同请求回放、幂等键冲突、跨用户隔离以及工具失败终态。当前仍未实现 SSE 和跨轮模型上下文组装。
