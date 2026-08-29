package com.sky.ai.persistence;

import java.time.LocalDateTime;

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

    public enum Status {
        BOT_ACTIVE,
        WAITING_HUMAN,
        HUMAN_ACTIVE,
        RESOLVED,
        CLOSED
    }

    public enum Handler {
        BOT,
        HUMAN,
        NONE
    }
}
