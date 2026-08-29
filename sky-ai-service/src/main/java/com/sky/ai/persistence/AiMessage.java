package com.sky.ai.persistence;

import java.time.LocalDateTime;

public record AiMessage(
        long id,
        String messageId,
        String conversationId,
        long sequenceNo,
        Role role,
        String content,
        Status status,
        String clientRequestId,
        String requestFingerprint,
        String replyToMessageId,
        String provider,
        String modelName,
        Integer inputTokens,
        Integer outputTokens,
        Integer latencyMs,
        String errorCode,
        String traceId,
        long version,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    public enum Role {
        USER,
        ASSISTANT,
        SYSTEM,
        TOOL
    }

    public enum Status {
        PENDING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}
