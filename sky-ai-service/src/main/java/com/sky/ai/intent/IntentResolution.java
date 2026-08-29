package com.sky.ai.intent;

public record IntentResolution(CustomerIntent intent,
                               double confidence,
                               ResolutionSource source,
                               Long orderId) {
}
