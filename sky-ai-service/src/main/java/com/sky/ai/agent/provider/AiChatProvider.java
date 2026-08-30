package com.sky.ai.agent.provider;

import java.util.List;

/**
 * 隔离模型厂商 SDK；Agent 编排层不直接依赖 Qwen 或 OpenAI 类型。
 */
public interface AiChatProvider {

    /**
     * 验证请求并执行一次客服对话。
     */
    AiChatResult chat(String systemPrompt, String userPrompt, List<AiChatTool> tools);
}
