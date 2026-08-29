package com.sky.ai.model;

import java.util.List;

/**
 * 模型最终回答，以及本轮实际调用过的工具名称。
 */
public record AiChatResult(String content, List<String> toolsUsed) {

    public AiChatResult {
        content = content == null ? "" : content;
        toolsUsed = toolsUsed == null ? List.of() : List.copyOf(toolsUsed);
    }
}
