package com.sky.ai.api;

import java.time.LocalDateTime;

public record ConversationView(
        String conversationId,
        String title,
        String status,
        String currentHandler,
        long lastMessageSequence,
        LocalDateTime lastMessageTime,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
