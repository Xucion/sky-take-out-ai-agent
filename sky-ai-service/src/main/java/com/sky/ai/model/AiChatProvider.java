package com.sky.ai.model;

/**
 * 隔离模型厂商 SDK；Agent 编排层不直接依赖 Qwen 或 OpenAI 类型。
 */
public interface AiChatProvider {

    String chat(String systemPrompt, String userPrompt);
}
