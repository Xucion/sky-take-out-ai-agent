package com.sky.ai.intent;

public record RuleMatch(RuleMatchStatus status,
                        CustomerIntent intent,
                        double confidence,
                        Long orderId) {
}
