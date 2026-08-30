package com.sky.ai.conversation.persistence;

import java.time.LocalDateTime;

/**
 * 表示从数据库读取的 AI 客服会话。
 */
public record AiConversation(
        long id,
        String conversationId,
        long userId,
        String channel,
        String title,
        Status status,
        Handler currentHandler,
        Long relatedOrderId,
        long lastMessageSequence,
        LocalDateTime lastMessageTime,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        long version,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    /**
     * 会话生命周期状态。
     */
    public enum Status {
        BOT_ACTIVE,
        WAITING_HUMAN,
        HUMAN_ACTIVE,
        RESOLVED,
        CLOSED
    }

    /**
     * 当前负责处理会话的参与方。
     */
    public enum Handler {
        BOT,
        HUMAN,
        NONE
    }
}
