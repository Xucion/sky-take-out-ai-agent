package com.sky.ai.conversation.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ConversationRepository 负责对应聚合的数据库访问和归属校验。
 */
@Repository
public class ConversationRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 初始化 ConversationRepository，并注入其运行所需的依赖。
     */
    public ConversationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 创建并返回新的会话。
     */
    @Transactional
    public AiConversation create(long userId, String channel, String title) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        String normalizedChannel = channel == null ? "WEB"
                : PersistenceInputs.required(channel, "channel", 32);
        String normalizedTitle = PersistenceInputs.optional(title, "title", 100);
        String conversationId = UUID.randomUUID().toString();

        jdbcTemplate.update("""
                INSERT INTO ai_conversation
                    (conversation_id, user_id, channel, title)
                VALUES (?, ?, ?, ?)
                """, conversationId, userId, normalizedChannel, normalizedTitle);

        return findOwnedById(conversationId, userId).orElseThrow();
    }

    /**
     * 按编号查询当前用户拥有的记录。
     */
    public Optional<AiConversation> findOwnedById(String conversationId, long userId) {
        PersistenceInputs.required(conversationId, "conversationId", 64);
        if (userId <= 0) {
            return Optional.empty();
        }
        List<AiConversation> rows = jdbcTemplate.query("""
                SELECT id, conversation_id, user_id, channel, title, status,
                       current_handler, related_order_id, last_message_sequence,
                       last_message_time, started_at, ended_at, version,
                       create_time, update_time
                FROM ai_conversation
                WHERE conversation_id = ? AND user_id = ?
                """, ConversationRepository::mapConversation, conversationId, userId);
        return rows.stream().findFirst();
    }

    /**
     * 分页查询用户最近使用的会话。
     */
    public List<AiConversation> findRecentOwnedByUser(long userId, int limit, int offset) {
        if (userId <= 0) {
            return List.of();
        }
        if (limit < 1 || limit > 100 || offset < 0) {
            throw new IllegalArgumentException("invalid pagination");
        }
        return jdbcTemplate.query("""
                SELECT id, conversation_id, user_id, channel, title, status,
                       current_handler, related_order_id, last_message_sequence,
                       last_message_time, started_at, ended_at, version,
                       create_time, update_time
                FROM ai_conversation
                WHERE user_id = ?
                ORDER BY update_time DESC, id DESC
                LIMIT ? OFFSET ?
                """, ConversationRepository::mapConversation, userId, limit, offset);
    }

    /**
     * 更新会话当前关联的订单编号。
     */
    public boolean updateRelatedOrderId(String conversationId, long userId, long orderId) {
        PersistenceInputs.required(conversationId, "conversationId", 64);
        if (userId <= 0 || orderId <= 0) {
            throw new IllegalArgumentException("invalid related order update");
        }
        return jdbcTemplate.update("""
                UPDATE ai_conversation
                SET related_order_id = ?, version = version + 1
                WHERE conversation_id = ? AND user_id = ?
                """, orderId, conversationId, userId) == 1;
    }

    /**
     * 将数据库结果集映射为会话对象。
     */
    private static AiConversation mapConversation(ResultSet rs, int rowNum) throws SQLException {
        return new AiConversation(
                rs.getLong("id"),
                rs.getString("conversation_id"),
                rs.getLong("user_id"),
                rs.getString("channel"),
                rs.getString("title"),
                AiConversation.Status.valueOf(rs.getString("status")),
                AiConversation.Handler.valueOf(rs.getString("current_handler")),
                (Long) rs.getObject("related_order_id"),
                rs.getLong("last_message_sequence"),
                rs.getTimestamp("last_message_time") == null
                        ? null : rs.getTimestamp("last_message_time").toLocalDateTime(),
                rs.getTimestamp("started_at").toLocalDateTime(),
                rs.getTimestamp("ended_at") == null
                        ? null : rs.getTimestamp("ended_at").toLocalDateTime(),
                rs.getLong("version"),
                rs.getTimestamp("create_time").toLocalDateTime(),
                rs.getTimestamp("update_time").toLocalDateTime());
    }
}
