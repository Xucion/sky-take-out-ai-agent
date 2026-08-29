package com.sky.ai.intent;

import java.util.List;

public record ConversationContext(String conversationId,
                                  Long relatedOrderId,
                                  List<ContextMessage> recentMessages) {

    public ConversationContext {
        recentMessages = recentMessages == null ? List.of() : List.copyOf(recentMessages);
    }

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
