package com.sky.ai.api;

import java.time.Instant;

public record StreamErrorEvent(String eventId,
                               String conversationId,
                               String code,
                               String message,
                               String traceId,
                               Instant createdAt) {
}
