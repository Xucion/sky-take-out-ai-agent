package com.sky.ai.api;

import java.time.Instant;

public record MessageDeltaEvent(String eventId,
                                String conversationId,
                                String messageId,
                                int index,
                                String delta,
                                Instant createdAt) {
}
