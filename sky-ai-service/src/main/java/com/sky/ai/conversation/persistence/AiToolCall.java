package com.sky.ai.conversation.persistence;

import java.time.LocalDateTime;

/**
 * 表示一次经过审计的 AI 工具调用。
 */
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

    /**
     * 工具调用执行状态。
     */
    public enum Status {
        STARTED,
        SUCCEEDED,
        FAILED,
        TIMEOUT,
        REJECTED
    }
}
