package com.sky.ai.persistence;

import java.time.LocalDateTime;

public record AiToolCall(
        long id,
        String toolCallId,
        String conversationId,
        String messageId,
        String toolName,
        String parameterSummary,
        Status resultStatus,
        String resultCode,
        String resultSummary,
        Integer latencyMs,
        String idempotencyKey,
        String traceId,
        long version,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    public enum Status {
        STARTED,
        SUCCEEDED,
        FAILED,
        TIMEOUT,
        REJECTED
    }
}
