package com.sky.ai.agent.intent;

/**
 * 汇总意图识别结果及其使用的会话上下文。
 */
public record IntentRoutingResult(IntentResolution resolution,
                                  ConversationContext context) {
}
