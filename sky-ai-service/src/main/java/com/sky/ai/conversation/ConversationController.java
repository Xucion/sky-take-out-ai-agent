package com.sky.ai.conversation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供会话创建、分页查询和历史消息查询接口。
 */
@Validated
@RestController
@RequestMapping("/api/ai/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    /**
     * 初始化 ConversationController，并注入其运行所需的依赖。
     */
    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    /**
     * 为当前用户创建一条新的客服会话。
     */
    @PostMapping
    public ResponseEntity<ConversationModels.ConversationView> create(
            @RequestHeader(value = "authentication", required = false) String userToken,
            @Valid @RequestBody ConversationModels.CreateConversationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(conversationService.create(userToken, request));
    }

    /**
     * 分页返回当前用户最近使用的客服会话。
     */
    @GetMapping
    public ConversationModels.ConversationPageResponse list(
            @RequestHeader(value = "authentication", required = false) String userToken,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return conversationService.list(userToken, limit, offset);
    }

    /**
     * 返回当前用户拥有的指定客服会话。
     */
    @GetMapping("/{conversationId}")
    public ConversationModels.ConversationView get(
            @RequestHeader(value = "authentication", required = false) String userToken,
            @PathVariable @NotBlank @Size(max = 64) String conversationId) {
        return conversationService.get(userToken, conversationId);
    }

    /**
     * 使用消息序号游标分页返回指定会话的历史消息。
     */
    @GetMapping("/{conversationId}/messages")
    public ConversationModels.MessagePageResponse messages(
            @RequestHeader(value = "authentication", required = false) String userToken,
            @PathVariable @NotBlank @Size(max = 64) String conversationId,
            @RequestParam(defaultValue = "0") @Min(0) long afterSequence,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return conversationService.messages(userToken, conversationId, afterSequence, limit);
    }
}
