package com.sky.ai.conversation.persistence;

import java.time.LocalDateTime;

/**
 * 表示会话中的一条持久化消息。
 */
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

    /**
     * 消息发送方角色。
     */
    public enum Role {
        USER,
        ASSISTANT,
        SYSTEM,
        TOOL
    }

    /**
     * 消息处理状态。
     */
    public enum Status {
        PENDING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}
