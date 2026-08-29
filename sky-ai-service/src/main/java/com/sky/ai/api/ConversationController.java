package com.sky.ai.api;

import com.sky.ai.service.ConversationApplicationService;
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

@Validated
@RestController
@RequestMapping("/api/ai/conversations")
public class ConversationController {

    private final ConversationApplicationService conversationService;

    public ConversationController(ConversationApplicationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    public ResponseEntity<ConversationView> create(
            @RequestHeader(value = "authentication", required = false) String userToken,
            @Valid @RequestBody CreateConversationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(conversationService.create(userToken, request));
    }

    @GetMapping
    public ConversationPageResponse list(
            @RequestHeader(value = "authentication", required = false) String userToken,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return conversationService.list(userToken, limit, offset);
    }

    @GetMapping("/{conversationId}")
    public ConversationView get(
            @RequestHeader(value = "authentication", required = false) String userToken,
            @PathVariable @NotBlank @Size(max = 64) String conversationId) {
        return conversationService.get(userToken, conversationId);
    }

    @GetMapping("/{conversationId}/messages")
    public MessagePageResponse messages(
            @RequestHeader(value = "authentication", required = false) String userToken,
            @PathVariable @NotBlank @Size(max = 64) String conversationId,
            @RequestParam(defaultValue = "0") @Min(0) long afterSequence,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return conversationService.messages(userToken, conversationId, afterSequence, limit);
    }
}
