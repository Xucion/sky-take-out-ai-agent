package com.sky.ai.model;

import com.sky.ai.error.AiServiceException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    public AiChatResult chat(String systemPrompt, String userPrompt, List<AiChatTool> tools) {
        // system 与 user 消息分开提交，避免把系统约束拼进普通用户文本后弱化优先级。
        List<String> toolsUsed = Collections.synchronizedList(new ArrayList<>());
        ToolCallback[] callbacks = tools.stream()
                .map(tool -> FunctionToolCallback.builder(tool.name(), () -> {
                            toolsUsed.add(tool.name());
                            return tool.execute();
                        })
                        .description(tool.description())
                        .build())
                .toArray(ToolCallback[]::new);

        try {
            String content = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .tools((Object[]) callbacks)
                    .call()
                    .content();
            return new AiChatResult(content, toolsUsed.stream().distinct().toList());
        } catch (ToolExecutionException ex) {
            // 保留业务工具的稳定 HTTP 状态和错误码，不让 Spring AI 包装成通用 500。
            if (ex.getCause() instanceof AiServiceException known) {
                throw known;
            }
            throw ex;
        }
    }
}
