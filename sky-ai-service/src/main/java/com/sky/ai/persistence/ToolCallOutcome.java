package com.sky.ai.persistence;

/**
 * resultSummary 必须是脱敏且截断后的结果摘要，不能包含完整业务响应。
 */
public record ToolCallOutcome(
        AiToolCall.Status status,
        String resultCode,
        String resultSummary,
        Integer latencyMs) {
}
