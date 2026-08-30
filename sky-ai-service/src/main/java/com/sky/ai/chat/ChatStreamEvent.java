package com.sky.ai.chat;

import java.time.Instant;

/**
 * SSE 聊天流允许发送的事件类型。
 */
public sealed interface ChatStreamEvent {

    /**
     * 表示回答正文中的一个增量片段。
     */
    record MessageDelta(
            String eventId,
            String conversationId,
            String messageId,
            int index,
            String delta,
            Instant createdAt) implements ChatStreamEvent {
    }

    /**
     * 表示一条助手消息已经完整生成并持久化。
     */
    record MessageCompleted(
            String eventId,
            String conversationId,
            String messageId,
            String answer,
            String traceId,
            boolean replayed,
            Instant createdAt) implements ChatStreamEvent {
    }

    /**
     * 表示流式处理过程中发生的可公开错误。
     */
    record StreamError(
            String eventId,
            String conversationId,
            String code,
            String message,
            String traceId,
            Instant createdAt) implements ChatStreamEvent {
    }
}
