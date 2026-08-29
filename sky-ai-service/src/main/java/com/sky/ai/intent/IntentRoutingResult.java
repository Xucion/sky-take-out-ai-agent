package com.sky.ai.intent;

public record IntentRoutingResult(IntentResolution resolution,
                                  ConversationContext context) {
}
