-- AI 客服会话、消息和工具调用审计表。
-- 目标数据库：sky_take_out（MySQL 8.0+）。执行前请备份数据库。
-- 本迁移只创建 AI 专属表，不修改 user、orders 等交易业务表。

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `ai_conversation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部主键，不对客户端暴露',
    `conversation_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '对外会话ID，由AI服务生成',
    `user_id` BIGINT NOT NULL COMMENT '会话所属用户ID，只能来自认证上下文',
    `channel` VARCHAR(32) NOT NULL DEFAULT 'WEB' COMMENT '会话渠道：WEB等',
    `title` VARCHAR(100) NULL COMMENT '用户可见的会话标题',
    `status` VARCHAR(32) NOT NULL DEFAULT 'BOT_ACTIVE' COMMENT '状态：BOT_ACTIVE/WAITING_HUMAN/HUMAN_ACTIVE/RESOLVED/CLOSED',
    `current_handler` VARCHAR(16) NOT NULL DEFAULT 'BOT' COMMENT '当前处理方：BOT/HUMAN/NONE',
    `related_order_id` BIGINT NULL COMMENT '可选关联订单ID，仅作关联，不建立交易表外键',
    `last_message_sequence` BIGINT NOT NULL DEFAULT 0 COMMENT '已分配的最后消息序号，用于会话内原子排序',
    `last_message_time` DATETIME(3) NULL COMMENT '最后一条消息时间',
    `started_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '会话开始时间',
    `ended_at` DATETIME(3) NULL COMMENT '会话结束时间',
    `version` BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_conversation_conversation_id` (`conversation_id`),
    KEY `idx_ai_conversation_user_updated` (`user_id`, `update_time`, `id`),
    KEY `idx_ai_conversation_status_updated` (`status`, `update_time`, `id`),
    KEY `idx_ai_conversation_related_order` (`related_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI客服会话';

CREATE TABLE IF NOT EXISTS `ai_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部主键',
    `message_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '对外消息ID，由AI服务生成',
    `conversation_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属对外会话ID',
    `sequence_no` BIGINT NOT NULL COMMENT '会话内严格递增序号',
    `role` VARCHAR(16) NOT NULL COMMENT '角色：USER/ASSISTANT/SYSTEM/TOOL',
    `content` MEDIUMTEXT NOT NULL COMMENT '持久化的用户可见消息正文',
    `status` VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/COMPLETED/FAILED/CANCELLED',
    `client_request_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '客户端请求幂等键；同一会话内唯一',
    `provider` VARCHAR(32) NULL COMMENT '模型提供方；用户消息为空',
    `model_name` VARCHAR(100) NULL COMMENT '模型名称或版本；用户消息为空',
    `input_tokens` INT UNSIGNED NULL COMMENT '输入Token数',
    `output_tokens` INT UNSIGNED NULL COMMENT '输出Token数',
    `latency_ms` INT UNSIGNED NULL COMMENT '生成完整消息耗时（毫秒）',
    `error_code` VARCHAR(64) NULL COMMENT '稳定、安全的错误码；不得保存异常堆栈',
    `trace_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '链路追踪ID',
    `version` BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_message_message_id` (`message_id`),
    UNIQUE KEY `uk_ai_message_conversation_sequence` (`conversation_id`, `sequence_no`),
    UNIQUE KEY `uk_ai_message_conversation_request` (`conversation_id`, `client_request_id`),
    KEY `idx_ai_message_conversation_created` (`conversation_id`, `create_time`, `id`),
    KEY `idx_ai_message_trace` (`trace_id`),
    CONSTRAINT `fk_ai_message_conversation`
        FOREIGN KEY (`conversation_id`) REFERENCES `ai_conversation` (`conversation_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI客服消息';

CREATE TABLE IF NOT EXISTS `ai_tool_call` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部主键',
    `tool_call_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工具调用ID，由AI服务生成',
    `conversation_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属对外会话ID',
    `message_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '触发调用的助手消息ID',
    `tool_name` VARCHAR(100) NOT NULL COMMENT '白名单工具名称',
    `parameter_summary` JSON NULL COMMENT '脱敏后的参数摘要；禁止保存JWT、密钥及完整敏感参数',
    `result_status` VARCHAR(24) NOT NULL DEFAULT 'STARTED' COMMENT '状态：STARTED/SUCCEEDED/FAILED/TIMEOUT/REJECTED',
    `result_code` VARCHAR(64) NULL COMMENT '稳定业务结果码或错误码',
    `result_summary` VARCHAR(1000) NULL COMMENT '脱敏、截断后的结果摘要；禁止保存完整业务响应',
    `latency_ms` INT UNSIGNED NULL COMMENT '工具调用耗时（毫秒）',
    `idempotency_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '写工具幂等键；只读工具可为空',
    `trace_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '链路追踪ID',
    `version` BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '调用开始时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_tool_call_tool_call_id` (`tool_call_id`),
    UNIQUE KEY `uk_ai_tool_call_idempotency` (`idempotency_key`),
    KEY `idx_ai_tool_call_conversation_created` (`conversation_id`, `create_time`, `id`),
    KEY `idx_ai_tool_call_message` (`message_id`),
    KEY `idx_ai_tool_call_tool_status_created` (`tool_name`, `result_status`, `create_time`),
    KEY `idx_ai_tool_call_trace` (`trace_id`),
    CONSTRAINT `fk_ai_tool_call_conversation`
        FOREIGN KEY (`conversation_id`) REFERENCES `ai_conversation` (`conversation_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_ai_tool_call_message`
        FOREIGN KEY (`message_id`) REFERENCES `ai_message` (`message_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI工具调用审计';
