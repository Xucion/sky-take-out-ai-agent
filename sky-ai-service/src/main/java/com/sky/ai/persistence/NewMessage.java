package com.sky.ai.persistence;

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

    public static NewMessage completedUserMessage(String conversationId,
                                                  String content,
                                                  String clientRequestId,
                                                  String traceId) {
        return new NewMessage(conversationId, AiMessage.Role.USER, content,
                AiMessage.Status.COMPLETED, clientRequestId, null, null, null, null,
                null, null, null, null, traceId);
    }

    public static NewMessage pendingAssistantMessage(String conversationId, String traceId) {
        return new NewMessage(conversationId, AiMessage.Role.ASSISTANT, "",
                AiMessage.Status.PENDING, null, null, null, null, null,
                null, null, null, null, traceId);
    }

    public static NewMessage completedUserMessage(String conversationId,
                                                  String content,
                                                  String clientRequestId,
                                                  String requestFingerprint,
                                                  String traceId) {
        return new NewMessage(conversationId, AiMessage.Role.USER, content,
                AiMessage.Status.COMPLETED, clientRequestId, requestFingerprint, null,
                null, null, null, null, null, null, traceId);
    }

    public static NewMessage pendingAssistantReply(String conversationId,
                                                   String userMessageId,
                                                   String traceId) {
        return new NewMessage(conversationId, AiMessage.Role.ASSISTANT, "",
                AiMessage.Status.PENDING, null, null, userMessageId,
                null, null, null, null, null, null, traceId);
    }
}
