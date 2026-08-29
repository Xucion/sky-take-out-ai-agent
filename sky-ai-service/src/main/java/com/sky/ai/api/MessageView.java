package com.sky.ai.api;

import java.time.LocalDateTime;

public record MessageView(
        String messageId,
        long sequenceNo,
        String role,
        String content,
        String status,
        String errorCode,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
