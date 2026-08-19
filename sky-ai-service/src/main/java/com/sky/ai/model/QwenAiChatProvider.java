package com.sky.ai.model;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Qwen 通过 OpenAI-compatible 协议接入。只有 qwen profile 和模型配置完整时才创建。
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "qwen")
public class QwenAiChatProvider implements AiChatProvider {

    private final ChatClient chatClient;

    public QwenAiChatProvider(ChatModel chatModel) {
        // ChatModel 由 Spring AI OpenAI-compatible 自动配置创建，业务代码不接触 API Key。
        this.chatClient = ChatClient.create(chatModel);
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        // system 与 user 消息分开提交，避免把系统约束拼进普通用户文本后弱化优先级。
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
    }
}
