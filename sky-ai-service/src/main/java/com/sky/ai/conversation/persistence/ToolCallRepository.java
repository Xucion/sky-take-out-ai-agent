package com.sky.ai.conversation.persistence;

import com.sky.ai.common.exception.AiServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * ToolCallRepository 负责对应聚合的数据库访问和归属校验。
 */
@Repository
public class ToolCallRepository {

    private static final Set<String> FORBIDDEN_SUMMARY_KEYS = Set.of(
            "authorization", "cookie", "jwt", "token", "secret", "password", "apikey",
            "userid", "phone", "address", "remark", "paymentcredential", "cardnumber",
            "verificationcode");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 初始化 ToolCallRepository，并注入其运行所需的依赖。
     */
    public ToolCallRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建或复用幂等的工具调用审计记录。
     */
    @Transactional
    public AiToolCall start(long userId, NewToolCall toolCall) {
        validateNewToolCall(userId, toolCall);
        lockOwnedConversation(toolCall.conversationId(), userId);

        if (toolCall.idempotencyKey() != null) {
            Optional<AiToolCall> existing = findOwnedByIdempotencyKey(
                    toolCall.idempotencyKey(), userId);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        if (toolCall.messageId() != null && !messageBelongsToOwnedConversation(
                toolCall.messageId(), toolCall.conversationId(), userId)) {
            throw new AiServiceException(HttpStatus.NOT_FOUND,
                    "MESSAGE_NOT_FOUND", "未找到关联消息");
        }

        String parameterSummary = serializeSummary(toolCall.parameterSummary());
        String toolCallId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO ai_tool_call
                    (tool_call_id, conversation_id, message_id, tool_name,
                     parameter_summary, result_status, idempotency_key, trace_id)
                VALUES (?, ?, ?, ?, ?, 'STARTED', ?, ?)
                """, toolCallId, toolCall.conversationId(), toolCall.messageId(),
                toolCall.toolName(), parameterSummary, toolCall.idempotencyKey(),
                toolCall.traceId());

        return findOwnedById(toolCallId, userId).orElseThrow();
    }

    /**
     * 按编号查询当前用户拥有的记录。
     */
    public Optional<AiToolCall> findOwnedById(String toolCallId, long userId) {
        PersistenceInputs.required(toolCallId, "toolCallId", 64);
        if (userId <= 0) {
            return Optional.empty();
        }
        List<AiToolCall> rows = jdbcTemplate.query("""
                SELECT t.*
                FROM ai_tool_call t
                JOIN ai_conversation c ON c.conversation_id = t.conversation_id
                WHERE t.tool_call_id = ? AND c.user_id = ?
                """, ToolCallRepository::mapToolCall, toolCallId, userId);
        return rows.stream().findFirst();
    }

    /**
     * 查询当前用户会话中的工具调用记录。
     */
    public List<AiToolCall> findOwnedByConversation(String conversationId,
                                                    long userId,
                                                    int limit) {
        PersistenceInputs.required(conversationId, "conversationId", 64);
        if (userId <= 0) {
            return List.of();
        }
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("invalid tool call limit");
        }
        return jdbcTemplate.query("""
                SELECT t.*
                FROM ai_tool_call t
                JOIN ai_conversation c ON c.conversation_id = t.conversation_id
                WHERE t.conversation_id = ? AND c.user_id = ?
                ORDER BY t.create_time ASC, t.id ASC
                LIMIT ?
                """, ToolCallRepository::mapToolCall, conversationId, userId, limit);
    }

    /**
     * 查询指定消息关联的工具调用记录。
     */
    public List<AiToolCall> findOwnedByMessage(String messageId, long userId, int limit) {
        PersistenceInputs.required(messageId, "messageId", 64);
        if (userId <= 0) {
            return List.of();
        }
        if (limit < 1 || limit > 20) {
            throw new IllegalArgumentException("invalid message tool call limit");
        }
        return jdbcTemplate.query("""
                SELECT t.*
                FROM ai_tool_call t
                JOIN ai_conversation c ON c.conversation_id = t.conversation_id
                WHERE t.message_id = ? AND c.user_id = ?
                ORDER BY t.create_time ASC, t.id ASC
                LIMIT ?
                """, ToolCallRepository::mapToolCall, messageId, userId, limit);
    }

    /**
     * 使用乐观锁完成工具调用审计。
     */
    public boolean finish(String toolCallId,
                          long userId,
                          long expectedVersion,
                          ToolCallOutcome outcome) {
        PersistenceInputs.required(toolCallId, "toolCallId", 64);
        if (userId <= 0 || expectedVersion < 0 || outcome == null || outcome.status() == null
                || outcome.status() == AiToolCall.Status.STARTED) {
            throw new IllegalArgumentException("invalid tool call outcome");
        }
        String resultCode = PersistenceInputs.optional(outcome.resultCode(), "resultCode", 64);
        String resultSummary = PersistenceInputs.optional(
                outcome.resultSummary(), "resultSummary", 1000);
        Integer latencyMs = PersistenceInputs.nonNegative(outcome.latencyMs(), "latencyMs");

        int updated = jdbcTemplate.update("""
                UPDATE ai_tool_call t
                JOIN ai_conversation c ON c.conversation_id = t.conversation_id
                SET t.result_status = ?,
                    t.result_code = ?,
                    t.result_summary = ?,
                    t.latency_ms = ?,
                    t.version = t.version + 1
                WHERE t.tool_call_id = ?
                  AND c.user_id = ?
                  AND t.result_status = 'STARTED'
                  AND t.version = ?
                """, outcome.status().name(), resultCode, resultSummary, latencyMs,
                toolCallId, userId, expectedVersion);
        return updated == 1;
    }

    /**
     * 按幂等键查询用户拥有的工具调用。
     */
    private Optional<AiToolCall> findOwnedByIdempotencyKey(String idempotencyKey, long userId) {
        List<AiToolCall> rows = jdbcTemplate.query("""
                SELECT t.*
                FROM ai_tool_call t
                JOIN ai_conversation c ON c.conversation_id = t.conversation_id
                WHERE t.idempotency_key = ? AND c.user_id = ?
                """, ToolCallRepository::mapToolCall, idempotencyKey, userId);
        return rows.stream().findFirst();
    }

    /**
     * 锁定并校验当前用户拥有的会话。
     */
    private void lockOwnedConversation(String conversationId, long userId) {
        List<Long> rows = jdbcTemplate.query("""
                SELECT id
                FROM ai_conversation
                WHERE conversation_id = ? AND user_id = ?
                FOR UPDATE
                """, (rs, rowNum) -> rs.getLong(1), conversationId, userId);
        if (rows.isEmpty()) {
            throw new AiServiceException(HttpStatus.NOT_FOUND,
                    "CONVERSATION_NOT_FOUND", "未找到该会话");
        }
    }

    /**
     * 校验消息属于当前用户拥有的会话。
     */
    private boolean messageBelongsToOwnedConversation(String messageId,
                                                       String conversationId,
                                                       long userId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM ai_message m
                JOIN ai_conversation c ON c.conversation_id = m.conversation_id
                WHERE m.message_id = ?
                  AND m.conversation_id = ?
                  AND c.user_id = ?
                """, Integer.class, messageId, conversationId, userId);
        return count != null && count == 1;
    }

    /**
     * 将脱敏摘要序列化为 JSON。
     */
    private String serializeSummary(Map<String, ?> summary) {
        Map<String, ?> safeSummary = summary == null ? Map.of() : summary;
        rejectForbiddenKeys(safeSummary);
        try {
            String json = objectMapper.writeValueAsString(safeSummary);
            if (json.length() > 4000) {
                throw new IllegalArgumentException("parameterSummary exceeds 4000 characters");
            }
            return json;
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("parameterSummary is not serializable", ex);
        }
    }

    /**
     * 拒绝摘要中可能泄漏敏感信息的字段。
     */
    private void rejectForbiddenKeys(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT)
                        .replace("_", "").replace("-", "");
                if (FORBIDDEN_SUMMARY_KEYS.stream().anyMatch(key::contains)) {
                    throw new IllegalArgumentException("parameterSummary contains a forbidden key");
                }
                rejectForbiddenKeys(entry.getValue());
            }
        } else if (value instanceof Iterable<?> iterable) {
            iterable.forEach(this::rejectForbiddenKeys);
        }
    }

    /**
     * 校验新工具调用的字段和资源归属。
     */
    private void validateNewToolCall(long userId, NewToolCall toolCall) {
        if (userId <= 0 || toolCall == null) {
            throw new IllegalArgumentException("invalid new tool call");
        }
        PersistenceInputs.required(toolCall.conversationId(), "conversationId", 64);
        PersistenceInputs.optional(toolCall.messageId(), "messageId", 64);
        PersistenceInputs.required(toolCall.toolName(), "toolName", 100);
        PersistenceInputs.optional(toolCall.idempotencyKey(), "idempotencyKey", 128);
        PersistenceInputs.required(toolCall.traceId(), "traceId", 64);
    }

    /**
     * 将数据库结果集映射为工具调用对象。
     */
    private static AiToolCall mapToolCall(ResultSet rs, int rowNum) throws SQLException {
        return new AiToolCall(
                rs.getLong("id"),
                rs.getString("tool_call_id"),
                rs.getString("conversation_id"),
                rs.getString("message_id"),
                rs.getString("tool_name"),
                rs.getString("parameter_summary"),
                AiToolCall.Status.valueOf(rs.getString("result_status")),
                rs.getString("result_code"),
                rs.getString("result_summary"),
                PersistenceInputs.nullableInteger(rs, "latency_ms"),
                rs.getString("idempotency_key"),
                rs.getString("trace_id"),
                rs.getLong("version"),
                rs.getTimestamp("create_time").toLocalDateTime(),
                rs.getTimestamp("update_time").toLocalDateTime());
    }
}
