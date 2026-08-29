package com.sky.ai.persistence;

import com.sky.ai.error.AiServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MessageRepository {

    private final JdbcTemplate jdbcTemplate;

    public MessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public AiMessage append(long userId, NewMessage message) {
        return appendWithResult(userId, message).message();
    }

    @Transactional
    public MessageAppendResult appendWithResult(long userId, NewMessage message) {
        validateNewMessage(userId, message);
        long currentSequence = lockOwnedConversation(message.conversationId(), userId);

        if (message.clientRequestId() != null) {
            Optional<AiMessage> existing = findOwnedByClientRequestId(
                    message.conversationId(), userId, message.clientRequestId());
            if (existing.isPresent()) {
                return new MessageAppendResult(existing.get(), false);
            }
        }

        if (message.replyToMessageId() != null) {
            Optional<AiMessage> existing = findReplyOwnedByUser(
                    message.conversationId(), userId, message.replyToMessageId());
            if (existing.isPresent()) {
                return new MessageAppendResult(existing.get(), false);
            }
            if (!isOwnedUserMessage(message.replyToMessageId(), message.conversationId(), userId)) {
                throw new AiServiceException(HttpStatus.NOT_FOUND,
                        "MESSAGE_NOT_FOUND", "未找到被回复的用户消息");
            }
        }

        long sequenceNo = currentSequence + 1;
        String messageId = UUID.randomUUID().toString();
        int conversationUpdated = jdbcTemplate.update("""
                UPDATE ai_conversation
                SET last_message_sequence = ?,
                    last_message_time = CURRENT_TIMESTAMP(3),
                    version = version + 1
                WHERE conversation_id = ? AND user_id = ?
                """, sequenceNo, message.conversationId(), userId);
        if (conversationUpdated != 1) {
            throw conversationNotFound();
        }

        jdbcTemplate.update("""
                INSERT INTO ai_message
                    (message_id, conversation_id, sequence_no, role, content, status,
                     client_request_id, request_fingerprint, reply_to_message_id,
                     provider, model_name, input_tokens, output_tokens,
                     latency_ms, error_code, trace_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                messageId, message.conversationId(), sequenceNo, message.role().name(),
                message.content(), message.status().name(), message.clientRequestId(),
                message.requestFingerprint(), message.replyToMessageId(), message.provider(),
                message.modelName(), message.inputTokens(),
                message.outputTokens(), message.latencyMs(), message.errorCode(), message.traceId());

        return new MessageAppendResult(findOwnedById(messageId, userId).orElseThrow(), true);
    }

    public Optional<AiMessage> findOwnedById(String messageId, long userId) {
        PersistenceInputs.required(messageId, "messageId", 64);
        if (userId <= 0) {
            return Optional.empty();
        }
        List<AiMessage> rows = jdbcTemplate.query("""
                SELECT m.*
                FROM ai_message m
                JOIN ai_conversation c ON c.conversation_id = m.conversation_id
                WHERE m.message_id = ? AND c.user_id = ?
                """, MessageRepository::mapMessage, messageId, userId);
        return rows.stream().findFirst();
    }

    public Optional<AiMessage> findOwnedByClientRequestId(String conversationId,
                                                          long userId,
                                                          String clientRequestId) {
        PersistenceInputs.required(conversationId, "conversationId", 64);
        PersistenceInputs.required(clientRequestId, "clientRequestId", 64);
        if (userId <= 0) {
            return Optional.empty();
        }
        List<AiMessage> rows = jdbcTemplate.query("""
                SELECT m.*
                FROM ai_message m
                JOIN ai_conversation c ON c.conversation_id = m.conversation_id
                WHERE m.conversation_id = ?
                  AND m.client_request_id = ?
                  AND c.user_id = ?
                """, MessageRepository::mapMessage,
                conversationId, clientRequestId, userId);
        return rows.stream().findFirst();
    }

    public Optional<AiMessage> findReplyOwnedByUser(String conversationId,
                                                    long userId,
                                                    String userMessageId) {
        PersistenceInputs.required(conversationId, "conversationId", 64);
        PersistenceInputs.required(userMessageId, "userMessageId", 64);
        if (userId <= 0) {
            return Optional.empty();
        }
        List<AiMessage> rows = jdbcTemplate.query("""
                SELECT reply.*
                FROM ai_message reply
                JOIN ai_conversation c ON c.conversation_id = reply.conversation_id
                WHERE reply.conversation_id = ?
                  AND reply.reply_to_message_id = ?
                  AND c.user_id = ?
                """, MessageRepository::mapMessage, conversationId, userMessageId, userId);
        return rows.stream().findFirst();
    }

    public List<AiMessage> findHistoryOwnedByUser(String conversationId,
                                                  long userId,
                                                  int limit,
                                                  long afterSequence) {
        PersistenceInputs.required(conversationId, "conversationId", 64);
        if (userId <= 0) {
            return List.of();
        }
        if (limit < 1 || limit > 200 || afterSequence < 0) {
            throw new IllegalArgumentException("invalid history pagination");
        }
        return jdbcTemplate.query("""
                SELECT m.*
                FROM ai_message m
                JOIN ai_conversation c ON c.conversation_id = m.conversation_id
                WHERE m.conversation_id = ?
                  AND m.sequence_no > ?
                  AND c.user_id = ?
                ORDER BY m.sequence_no ASC
                LIMIT ?
                """, MessageRepository::mapMessage,
                conversationId, afterSequence, userId, limit);
    }

    public List<AiMessage> findRecentBeforeSequenceOwnedByUser(String conversationId,
                                                               long userId,
                                                               long beforeSequence,
                                                               int limit) {
        PersistenceInputs.required(conversationId, "conversationId", 64);
        if (userId <= 0) {
            return List.of();
        }
        if (beforeSequence < 1 || limit < 1 || limit > 50) {
            throw new IllegalArgumentException("invalid recent history query");
        }
        List<AiMessage> rows = jdbcTemplate.query("""
                SELECT m.*
                FROM ai_message m
                JOIN ai_conversation c ON c.conversation_id = m.conversation_id
                WHERE m.conversation_id = ?
                  AND m.sequence_no < ?
                  AND c.user_id = ?
                  AND m.status = 'COMPLETED'
                  AND m.role IN ('USER', 'ASSISTANT')
                ORDER BY m.sequence_no DESC
                LIMIT ?
                """, MessageRepository::mapMessage,
                conversationId, beforeSequence, userId, limit);
        return rows.reversed();
    }

    public boolean updateOutcome(String messageId,
                                 long userId,
                                 long expectedVersion,
                                 MessageOutcome outcome) {
        PersistenceInputs.required(messageId, "messageId", 64);
        if (userId <= 0 || expectedVersion < 0 || outcome == null || outcome.status() == null
                || outcome.status() == AiMessage.Status.PENDING) {
            throw new IllegalArgumentException("invalid message outcome");
        }
        String content = PersistenceInputs.optional(outcome.content(), "content", 100_000);
        String provider = PersistenceInputs.optional(outcome.provider(), "provider", 32);
        String modelName = PersistenceInputs.optional(outcome.modelName(), "modelName", 100);
        String errorCode = PersistenceInputs.optional(outcome.errorCode(), "errorCode", 64);

        int updated = jdbcTemplate.update("""
                UPDATE ai_message m
                JOIN ai_conversation c ON c.conversation_id = m.conversation_id
                SET m.content = ?,
                    m.status = ?,
                    m.provider = ?,
                    m.model_name = ?,
                    m.input_tokens = ?,
                    m.output_tokens = ?,
                    m.latency_ms = ?,
                    m.error_code = ?,
                    m.version = m.version + 1
                WHERE m.message_id = ?
                  AND c.user_id = ?
                  AND m.role = 'ASSISTANT'
                  AND m.status = 'PENDING'
                  AND m.version = ?
                """,
                content == null ? "" : content, outcome.status().name(), provider, modelName,
                PersistenceInputs.nonNegative(outcome.inputTokens(), "inputTokens"),
                PersistenceInputs.nonNegative(outcome.outputTokens(), "outputTokens"),
                PersistenceInputs.nonNegative(outcome.latencyMs(), "latencyMs"), errorCode,
                messageId, userId, expectedVersion);
        return updated == 1;
    }

    private long lockOwnedConversation(String conversationId, long userId) {
        List<Long> rows = jdbcTemplate.query("""
                SELECT last_message_sequence
                FROM ai_conversation
                WHERE conversation_id = ? AND user_id = ?
                FOR UPDATE
                """, (rs, rowNum) -> rs.getLong(1), conversationId, userId);
        if (rows.isEmpty()) {
            throw conversationNotFound();
        }
        return rows.getFirst();
    }

    private void validateNewMessage(long userId, NewMessage message) {
        if (userId <= 0 || message == null || message.role() == null || message.status() == null) {
            throw new IllegalArgumentException("invalid new message");
        }
        PersistenceInputs.required(message.conversationId(), "conversationId", 64);
        if (message.content() == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        PersistenceInputs.optional(message.content(), "content", 100_000);
        PersistenceInputs.optional(message.clientRequestId(), "clientRequestId", 64);
        PersistenceInputs.optional(message.requestFingerprint(), "requestFingerprint", 64);
        PersistenceInputs.optional(message.replyToMessageId(), "replyToMessageId", 64);
        PersistenceInputs.optional(message.provider(), "provider", 32);
        PersistenceInputs.optional(message.modelName(), "modelName", 100);
        PersistenceInputs.optional(message.errorCode(), "errorCode", 64);
        PersistenceInputs.required(message.traceId(), "traceId", 64);
        PersistenceInputs.nonNegative(message.inputTokens(), "inputTokens");
        PersistenceInputs.nonNegative(message.outputTokens(), "outputTokens");
        PersistenceInputs.nonNegative(message.latencyMs(), "latencyMs");
        if (message.role() == AiMessage.Role.USER && message.clientRequestId() == null) {
            throw new IllegalArgumentException("user message requires clientRequestId");
        }
        if (message.role() != AiMessage.Role.USER && message.clientRequestId() != null) {
            throw new IllegalArgumentException("clientRequestId is reserved for user messages");
        }
        if (message.role() != AiMessage.Role.USER && message.requestFingerprint() != null) {
            throw new IllegalArgumentException("requestFingerprint is reserved for user messages");
        }
        if (message.role() != AiMessage.Role.ASSISTANT && message.replyToMessageId() != null) {
            throw new IllegalArgumentException("replyToMessageId is reserved for assistant messages");
        }
    }

    private boolean isOwnedUserMessage(String messageId, String conversationId, long userId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM ai_message m
                JOIN ai_conversation c ON c.conversation_id = m.conversation_id
                WHERE m.message_id = ?
                  AND m.conversation_id = ?
                  AND m.role = 'USER'
                  AND c.user_id = ?
                """, Integer.class, messageId, conversationId, userId);
        return count != null && count == 1;
    }

    private static AiMessage mapMessage(ResultSet rs, int rowNum) throws SQLException {
        return new AiMessage(
                rs.getLong("id"),
                rs.getString("message_id"),
                rs.getString("conversation_id"),
                rs.getLong("sequence_no"),
                AiMessage.Role.valueOf(rs.getString("role")),
                rs.getString("content"),
                AiMessage.Status.valueOf(rs.getString("status")),
                rs.getString("client_request_id"),
                rs.getString("request_fingerprint"),
                rs.getString("reply_to_message_id"),
                rs.getString("provider"),
                rs.getString("model_name"),
                PersistenceInputs.nullableInteger(rs, "input_tokens"),
                PersistenceInputs.nullableInteger(rs, "output_tokens"),
                PersistenceInputs.nullableInteger(rs, "latency_ms"),
                rs.getString("error_code"),
                rs.getString("trace_id"),
                rs.getLong("version"),
                rs.getTimestamp("create_time").toLocalDateTime(),
                rs.getTimestamp("update_time").toLocalDateTime());
    }

    private static AiServiceException conversationNotFound() {
        return new AiServiceException(HttpStatus.NOT_FOUND,
                "CONVERSATION_NOT_FOUND", "未找到该会话");
    }
}
