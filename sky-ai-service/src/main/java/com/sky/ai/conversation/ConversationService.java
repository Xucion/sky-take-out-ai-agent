package com.sky.ai.conversation;

import com.sky.ai.common.exception.AiServiceException;
import com.sky.ai.conversation.persistence.AiConversation;
import com.sky.ai.conversation.persistence.AiMessage;
import com.sky.ai.conversation.persistence.ConversationRepository;
import com.sky.ai.conversation.persistence.MessageRepository;
import com.sky.ai.common.security.IdentityBridgeService;
import com.sky.ai.common.security.UserIdentity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 封装会话创建、会话查询、消息查询和归属校验业务。
 */
@Service
public class ConversationService {

    private static final int MAX_CONVERSATION_PAGE_SIZE = 50;
    private static final int MAX_MESSAGE_PAGE_SIZE = 100;

    private final IdentityBridgeService identityBridge;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    /**
     * 初始化 ConversationService，并注入其运行所需的依赖。
     */
    public ConversationService(IdentityBridgeService identityBridge,
                               ConversationRepository conversationRepository,
                               MessageRepository messageRepository) {
        this.identityBridge = identityBridge;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    /**
     * 创建并返回新的会话。
     */
    public ConversationModels.ConversationView create(String userToken, ConversationModels.CreateConversationRequest request) {
        UserIdentity identity = identityBridge.verifyUser(userToken);
        String title = normalizeTitle(request == null ? null : request.title());
        return toView(conversationRepository.create(identity.userId(), "WEB", title));
    }

    /**
     * 分页查询当前用户拥有的会话。
     */
    public ConversationModels.ConversationPageResponse list(String userToken, int limit, int offset) {
        validateConversationPage(limit, offset);
        UserIdentity identity = identityBridge.verifyUser(userToken);
        List<AiConversation> rows = new ArrayList<>(conversationRepository.findRecentOwnedByUser(
                identity.userId(), limit + 1, offset));
        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows.removeLast();
        }
        return new ConversationModels.ConversationPageResponse(rows.stream().map(this::toView).toList(),
                limit, offset, hasMore);
    }

    /**
     * 读取当前用户拥有的指定会话。
     */
    public ConversationModels.ConversationView get(String userToken, String conversationId) {
        UserIdentity identity = identityBridge.verifyUser(userToken);
        return toView(requireOwned(conversationId, identity.userId()));
    }

    /**
     * 校验当前用户拥有指定会话。
     */
    public void validateOwnership(String userToken, String conversationId) {
        UserIdentity identity = identityBridge.verifyUser(userToken);
        requireOwned(conversationId, identity.userId());
    }

    /**
     * 分页查询指定会话中的消息。
     */
    public ConversationModels.MessagePageResponse messages(String userToken,
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
        List<ConversationModels.MessageView> items = rows.stream().map(this::toView).toList();
        long nextAfterSequence = rows.isEmpty()
                ? afterSequence : rows.getLast().sequenceNo();
        return new ConversationModels.MessagePageResponse(items, limit, afterSequence, nextAfterSequence, hasMore);
    }

    /**
     * 读取指定会话并强制执行用户归属校验。
     */
    private AiConversation requireOwned(String conversationId, long userId) {
        return conversationRepository.findOwnedById(conversationId, userId)
                .orElseThrow(() -> new AiServiceException(HttpStatus.NOT_FOUND,
                        "CONVERSATION_NOT_FOUND", "未找到该会话"));
    }

    /**
     * 将持久化对象转换为接口视图。
     */
    private ConversationModels.ConversationView toView(AiConversation conversation) {
        return new ConversationModels.ConversationView(
                conversation.conversationId(), conversation.title(), conversation.status().name(),
                conversation.currentHandler().name(), conversation.lastMessageSequence(),
                conversation.lastMessageTime(), conversation.startedAt(), conversation.endedAt(),
                conversation.createTime(), conversation.updateTime());
    }

    /**
     * 将持久化对象转换为接口视图。
     */
    private ConversationModels.MessageView toView(AiMessage message) {
        return new ConversationModels.MessageView(message.messageId(), message.sequenceNo(), message.role().name(),
                message.content(), message.status().name(), message.errorCode(),
                message.createTime(), message.updateTime());
    }

    /**
     * 规范化用户提供的会话标题。
     */
    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        return title.trim();
    }

    /**
     * 校验会话分页参数。
     */
    private void validateConversationPage(int limit, int offset) {
        if (limit < 1 || limit > MAX_CONVERSATION_PAGE_SIZE || offset < 0) {
            throw invalidPagination();
        }
    }

    /**
     * 校验消息游标分页参数。
     */
    private void validateMessagePage(long afterSequence, int limit) {
        if (afterSequence < 0 || limit < 1 || limit > MAX_MESSAGE_PAGE_SIZE) {
            throw invalidPagination();
        }
    }

    /**
     * 创建分页参数不合法异常。
     */
    private AiServiceException invalidPagination() {
        return new AiServiceException(HttpStatus.BAD_REQUEST,
                "INVALID_ARGUMENT", "分页参数不正确");
    }
}
