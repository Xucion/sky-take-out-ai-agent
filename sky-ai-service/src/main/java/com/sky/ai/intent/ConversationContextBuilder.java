package com.sky.ai.intent;

import com.sky.ai.error.AiServiceException;
import com.sky.ai.persistence.AiConversation;
import com.sky.ai.persistence.AiMessage;
import com.sky.ai.persistence.ConversationRepository;
import com.sky.ai.persistence.MessageRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ConversationContextBuilder {

    private static final int RECENT_MESSAGE_LIMIT = 12;
    private static final int MAX_CONTEXT_MESSAGE_CHARS = 1000;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public ConversationContextBuilder(ConversationRepository conversationRepository,
                                      MessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

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

    private ContextMessage toContextMessage(AiMessage message) {
        String content = message.content().length() <= MAX_CONTEXT_MESSAGE_CHARS
                ? message.content()
                : message.content().substring(0, MAX_CONTEXT_MESSAGE_CHARS);
        String role = message.role() == AiMessage.Role.USER ? "用户" : "助手";
        return new ContextMessage(role, content);
    }
}
