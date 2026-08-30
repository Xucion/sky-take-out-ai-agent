package com.sky.ai.agent.intent;

/**
 * 表示最终识别出的意图、置信度、来源和订单编号。
 */
public record IntentResolution(CustomerIntent intent,
                               double confidence,
                               ResolutionSource source,
                               Long orderId) {
}
