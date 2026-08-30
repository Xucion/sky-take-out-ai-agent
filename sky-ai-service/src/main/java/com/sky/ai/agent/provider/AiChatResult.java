package com.sky.ai.agent.provider;

import java.util.List;

/**
 * 模型最终回答，以及本轮实际调用过的工具名称。
 */
public record AiChatResult(String content, List<String> toolsUsed) {

    /**
     * 初始化 AiChatResult，并注入其运行所需的依赖。
     */
    public AiChatResult {
        content = content == null ? "" : content;
        toolsUsed = toolsUsed == null ? List.of() : List.copyOf(toolsUsed);
    }
}
