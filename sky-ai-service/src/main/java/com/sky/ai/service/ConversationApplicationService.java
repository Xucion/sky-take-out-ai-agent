package com.sky.ai.service;

import com.sky.ai.api.ConversationPageResponse;
import com.sky.ai.api.ConversationView;
import com.sky.ai.api.CreateConversationRequest;
import com.sky.ai.api.MessagePageResponse;
import com.sky.ai.api.MessageView;
import com.sky.ai.error.AiServiceException;
import com.sky.ai.persistence.AiConversation;
import com.sky.ai.persistence.AiMessage;
import com.sky.ai.persistence.ConversationRepository;
import com.sky.ai.persistence.MessageRepository;
import com.sky.ai.security.IdentityBridgeService;
import com.sky.ai.security.UserIdentity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ConversationApplicationService {

    private static final int MAX_CONVERSATION_PAGE_SIZE = 50;
    private static final int MAX_MESSAGE_PAGE_SIZE = 100;

    private final IdentityBridgeService identityBridge;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public ConversationApplicationService(IdentityBridgeService identityBridge,
                                          ConversationRepository conversationRepository,
                                          MessageRepository messageRepository) {
        this.identityBridge = identityBridge;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    public ConversationView create(String userToken, CreateConversationRequest request) {
        UserIdentity identity = identityBridge.verifyUser(userToken);
        String title = normalizeTitle(request == null ? null : request.title());
        return toView(conversationRepository.create(identity.userId(), "WEB", title));
    }

    public ConversationPageResponse list(String userToken, int limit, int offset) {
        validateConversationPage(limit, offset);
        UserIdentity identity = identityBridge.verifyUser(userToken);
        List<AiConversation> rows = new ArrayList<>(conversationRepository.findRecentOwnedByUser(
                identity.userId(), limit + 1, offset));
        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows.removeLast();
        }
        return new ConversationPageResponse(rows.stream().map(this::toView).toList(),
                limit, offset, hasMore);
    }

    public ConversationView get(String userToken, String conversationId) {
        UserIdentity identity = identityBridge.verifyUser(userToken);
        return toView(requireOwned(conversationId, identity.userId()));
    }

    public void validateOwnership(String userToken, String conversationId) {
        UserIdentity identity = identityBridge.verifyUser(userToken);
        requireOwned(conversationId, identity.userId());
    }

    public MessagePageResponse messages(String userToken,
                                        String conversationId,
                                        long afterSequence,
                                        int limit) {
        validateMessagePage(afterSequence, limit);
        UserIdentity identity = identityBridge.verifyUser(userToken);
        requireOwned(conversationId, identity.userId());

        List<AiMessage> rows = new ArrayList<>(messageRepository.findHistoryOwnedByUser(
                conversationId, identity.userId(), limit + 1, afterSequence));
        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows.removeLast();
        }
        List<MessageView> items = rows.stream().map(this::toView).toList();
        long nextAfterSequence = rows.isEmpty()
                ? afterSequence : rows.getLast().sequenceNo();
        return new MessagePageResponse(items, limit, afterSequence, nextAfterSequence, hasMore);
    }

    private AiConversation requireOwned(String conversationId, long userId) {
        return conversationRepository.findOwnedById(conversationId, userId)
                .orElseThrow(() -> new AiServiceException(HttpStatus.NOT_FOUND,
                        "CONVERSATION_NOT_FOUND", "未找到该会话"));
    }

    private ConversationView toView(AiConversation conversation) {
        return new ConversationView(
                conversation.conversationId(), conversation.title(), conversation.status().name(),
                conversation.currentHandler().name(), conversation.lastMessageSequence(),
                conversation.lastMessageTime(), conversation.startedAt(), conversation.endedAt(),
                conversation.createTime(), conversation.updateTime());
    }

    private MessageView toView(AiMessage message) {
        return new MessageView(message.messageId(), message.sequenceNo(), message.role().name(),
                message.content(), message.status().name(), message.errorCode(),
                message.createTime(), message.updateTime());
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        return title.trim();
    }

    private void validateConversationPage(int limit, int offset) {
        if (limit < 1 || limit > MAX_CONVERSATION_PAGE_SIZE || offset < 0) {
            throw invalidPagination();
        }
    }

    private void validateMessagePage(long afterSequence, int limit) {
        if (afterSequence < 0 || limit < 1 || limit > MAX_MESSAGE_PAGE_SIZE) {
            throw invalidPagination();
        }
    }

    private AiServiceException invalidPagination() {
        return new AiServiceException(HttpStatus.BAD_REQUEST,
                "INVALID_ARGUMENT", "分页参数不正确");
    }
}
