package com.sky.ai.agent.intent;

import com.sky.ai.common.exception.AiServiceException;
import com.sky.ai.conversation.persistence.AiConversation;
import com.sky.ai.conversation.persistence.AiMessage;
import com.sky.ai.conversation.persistence.ConversationRepository;
import com.sky.ai.conversation.persistence.MessageRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ConversationContextBuilder 负责组装对应业务上下文。
 */
@Component
public class ConversationContextBuilder {

    private static final int RECENT_MESSAGE_LIMIT = 12;
    private static final int MAX_CONTEXT_MESSAGE_CHARS = 1000;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    /**
     * 初始化 ConversationContextBuilder，并注入其运行所需的依赖。
     */
    public ConversationContextBuilder(ConversationRepository conversationRepository,
                                      MessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    /**
     * 读取持久化消息并构建当前会话上下文。
     */
    public ConversationContext build(long userId,
                                     String conversationId,
                                     long beforeSequence) {
        AiConversation conversation = conversationRepository
                .findOwnedById(conversationId, userId)
                .orElseThrow(() -> new AiServiceException(HttpStatus.NOT_FOUND,
                        "CONVERSATION_NOT_FOUND", "未找到该会话"));
        List<ContextMessage> messages = messageRepository
                .findRecentBeforeSequenceOwnedByUser(
                        conversationId, userId, beforeSequence, RECENT_MESSAGE_LIMIT)
                .stream()
                .map(this::toContextMessage)
                .toList();
        return new ConversationContext(conversationId,
                conversation.relatedOrderId(), messages);
    }

    /**
     * 将持久化消息转换为模型上下文消息。
     */
    private ContextMessage toContextMessage(AiMessage message) {
        String content = message.content().length() <= MAX_CONTEXT_MESSAGE_CHARS
                ? message.content()
                : message.content().substring(0, MAX_CONTEXT_MESSAGE_CHARS);
        String role = message.role() == AiMessage.Role.USER ? "用户" : "助手";
        return new ContextMessage(role, content);
    }
}
