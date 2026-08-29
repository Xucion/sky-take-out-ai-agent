package com.sky.ai.persistence;

import java.util.Map;

/**
 * parameterSummary 必须是调用方按工具白名单构造的脱敏摘要，不能传入原始请求或请求头。
 */
public record NewToolCall(
        String conversationId,
        String messageId,
        String toolName,
        Map<String, ?> parameterSummary,
        String idempotencyKey,
        String traceId) {
}
