package com.sky.ai.conversation.persistence;

/**
 * 表示准备追加到会话中的新消息数据。
 */
public record NewMessage(
        String conversationId,
        AiMessage.Role role,
        String content,
        AiMessage.Status status,
        String clientRequestId,
        String requestFingerprint,
        String replyToMessageId,
        String provider,
        String modelName,
        Integer inputTokens,
        Integer outputTokens,
        Integer latencyMs,
        String errorCode,
        String traceId) {

    /**
     * 创建状态为已完成的用户消息。
     */
    public static NewMessage completedUserMessage(String conversationId,
                                                  String content,
                                                  String clientRequestId,
                                                  String traceId) {
        return new NewMessage(conversationId, AiMessage.Role.USER, content,
                AiMessage.Status.COMPLETED, clientRequestId, null, null, null, null,
                null, null, null, null, traceId);
    }

    /**
     * 创建等待生成内容的助手消息。
     */
    public static NewMessage pendingAssistantMessage(String conversationId, String traceId) {
        return new NewMessage(conversationId, AiMessage.Role.ASSISTANT, "",
                AiMessage.Status.PENDING, null, null, null, null, null,
                null, null, null, null, traceId);
    }

    /**
     * 创建状态为已完成的用户消息。
     */
    public static NewMessage completedUserMessage(String conversationId,
                                                  String content,
                                                  String clientRequestId,
                                                  String requestFingerprint,
                                                  String traceId) {
        return new NewMessage(conversationId, AiMessage.Role.USER, content,
                AiMessage.Status.COMPLETED, clientRequestId, requestFingerprint, null,
                null, null, null, null, null, null, traceId);
    }

    /**
     * 创建关联指定用户消息的待处理助手回复。
     */
    public static NewMessage pendingAssistantReply(String conversationId,
                                                   String userMessageId,
                                                   String traceId) {
        return new NewMessage(conversationId, AiMessage.Role.ASSISTANT, "",
                AiMessage.Status.PENDING, null, null, userMessageId,
                null, null, null, null, null, null, traceId);
    }
}
