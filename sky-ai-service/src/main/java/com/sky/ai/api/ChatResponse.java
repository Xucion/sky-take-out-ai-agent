package com.sky.ai.api;

public record ChatResponse(String answer,
                           String intent,
                           String toolUsed,
                           String provider,
                           String traceId) {
}
