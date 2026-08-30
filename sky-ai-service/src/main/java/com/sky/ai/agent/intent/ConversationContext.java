package com.sky.ai.agent.intent;

import java.util.List;

/**
 * 保存意图识别所需的会话、关联订单和近期消息上下文。
 */
public record ConversationContext(String conversationId,
                                  Long relatedOrderId,
                                  List<ContextMessage> recentMessages) {

    /**
     * 创建不可变的会话上下文，并将空消息列表规范化为空集合。
     */
    public ConversationContext {
        recentMessages = recentMessages == null ? List.of() : List.copyOf(recentMessages);
    }

    /**
     * 将近期消息转换为可传给模型的上下文文本。
     */
    public String promptText() {
        if (recentMessages.isEmpty()) {
            return "无历史消息";
        }
        StringBuilder prompt = new StringBuilder();
        for (ContextMessage message : recentMessages) {
            prompt.append(message.role()).append("：")
                    .append(message.content()).append('\n');
        }
        return prompt.toString().stripTrailing();
    }
}
