package com.sky.ai.conversation.persistence;

/**
 * 保存助手消息执行完成后的内容、模型用量和错误信息。
 */
public record MessageOutcome(
        String content,
        AiMessage.Status status,
        String provider,
        String modelName,
        Integer inputTokens,
        Integer outputTokens,
        Integer latencyMs,
        String errorCode) {
}
