package com.sky.ai.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 聊天接口使用的请求和响应模型集合。
 */
public final class ChatModels {

    /**
     * 禁止实例化仅用于归类模型的工具类。
     */
    private ChatModels() {
    }

    /**
     * 普通聊天请求；用户身份只允许从认证令牌获取。
     */
    public record ChatRequest(
            @NotBlank @Size(max = 64) String conversationId,
            @NotBlank @Size(max = 2000) String message,
            @NotBlank @Size(max = 64)
            @Pattern(regexp = "[A-Za-z0-9._:-]+") String clientRequestId) {
    }

    /**
     * 聊天处理完成后的统一响应。
     */
    public record ChatResponse(
            String answer,
            String intent,
            String toolUsed,
            String provider,
            String traceId,
            String userMessageId,
            String assistantMessageId,
            boolean replayed) {
    }

    /**
     * SSE 聊天接口的请求体，会话编号由路径参数提供。
     */
    public record StreamChatRequest(
            @NotBlank @Size(max = 2000) String message,
            @NotBlank @Size(max = 64)
            @Pattern(regexp = "[A-Za-z0-9._:-]+") String clientRequestId) {
    }
}
