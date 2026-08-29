package com.sky.ai.api;

import java.time.Instant;

public record MessageCompletedEvent(String eventId,
                                    String conversationId,
                                    String messageId,
                                    String answer,
                                    String traceId,
                                    boolean replayed,
                                    Instant createdAt) {
}
