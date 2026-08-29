package com.sky.ai.persistence;

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
