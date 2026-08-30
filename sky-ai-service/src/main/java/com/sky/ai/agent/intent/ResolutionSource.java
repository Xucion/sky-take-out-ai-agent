package com.sky.ai.agent.intent;

/**
 * 标识意图识别结果来自规则、上下文还是大模型。
 */
public enum ResolutionSource {
    RULE,
    CONTEXT,
    LLM
}
