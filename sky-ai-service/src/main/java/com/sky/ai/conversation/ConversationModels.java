package com.sky.ai.conversation;

import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话模块对外使用的请求和响应模型集合。
 */
public final class ConversationModels {

    /**
     * 禁止实例化仅用于归类模型的工具类。
     */
    private ConversationModels() {
    }

    /**
     * 创建会话时接收的可选标题。
     */
    public record CreateConversationRequest(@Size(max = 100) String title) {
    }

    /**
     * 返回给客户端的会话摘要视图。
     */
    public record ConversationView(
            String conversationId,
            String title,
            String status,
            String currentHandler,
            long lastMessageSequence,
            LocalDateTime lastMessageTime,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    /**
     * 会话列表的分页响应。
     */
    public record ConversationPageResponse(
            List<ConversationView> items,
            int limit,
            int offset,
            boolean hasMore) {
    }

    /**
     * 返回给客户端的单条消息视图。
     */
    public record MessageView(
            String messageId,
            long sequenceNo,
            String role,
            String content,
            String status,
            String errorCode,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    /**
     * 消息列表的游标分页响应。
     */
    public record MessagePageResponse(
            List<MessageView> items,
            int limit,
            long afterSequence,
            long nextAfterSequence,
            boolean hasMore) {
    }
}
