package com.sky.ai.api;

import com.sky.ai.error.AiServiceException;
import com.sky.ai.service.ConversationApplicationService;
import com.sky.ai.service.CustomerSupportAgentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

@Validated
@RestController
@RequestMapping("/api/ai/conversations")
public class StreamingChatController {

    private static final long SSE_TIMEOUT_MILLIS = 60_000L;
    private static final int DELTA_CODE_POINTS = 24;

    private final ConversationApplicationService conversationService;
    private final CustomerSupportAgentService agentService;
    private final Executor sseExecutor;

    public StreamingChatController(ConversationApplicationService conversationService,
                                   CustomerSupportAgentService agentService,
                                   @Qualifier("sseExecutor") Executor sseExecutor) {
        this.conversationService = conversationService;
        this.agentService = agentService;
        this.sseExecutor = sseExecutor;
    }

    @PostMapping(value = "/{conversationId}/messages/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestHeader(value = "authentication", required = false) String userToken,
            @RequestHeader(value = "X-Trace-Id", required = false) String suppliedTraceId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
            @PathVariable @NotBlank @Size(max = 64) String conversationId,
            @Valid @RequestBody StreamChatRequest request,
            HttpServletResponse servletResponse) {
        // 在返回 200 和建立事件流之前完成认证与归属校验。
        conversationService.validateOwnership(userToken, conversationId);
        servletResponse.setHeader("Cache-Control", "no-cache, no-transform");
        servletResponse.setHeader("X-Accel-Buffering", "no");
        String traceId = TraceIds.validOrRandom(suppliedTraceId);
        String resumeAfter = validLastEventId(lastEventId);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        sseExecutor.execute(() -> runStream(emitter, userToken, conversationId,
                request, traceId, resumeAfter));
        return emitter;
    }

    private void runStream(SseEmitter emitter,
                           String userToken,
                           String conversationId,
                           StreamChatRequest request,
                           String traceId,
                           String lastEventId) {
        try {
            ChatResponse response = agentService.chat(userToken,
                    new ChatRequest(conversationId, request.message(), request.orderId(),
                            request.clientRequestId()), traceId);
            sendAnswerEvents(emitter, conversationId, response, lastEventId);
            emitter.complete();
        } catch (AiServiceException ex) {
            sendError(emitter, conversationId, ex.getCode(), ex.getMessage(), traceId);
        } catch (RuntimeException ex) {
            sendError(emitter, conversationId, "INTERNAL_ERROR",
                    "客服暂时不可用，请稍后重试", traceId);
        }
    }

    private void sendAnswerEvents(SseEmitter emitter,
                                  String conversationId,
                                  ChatResponse response,
                                  String lastEventId) {
        List<EventToSend> events = new ArrayList<>();
        List<String> chunks = splitByCodePoints(response.answer(), DELTA_CODE_POINTS);
        for (int index = 0; index < chunks.size(); index++) {
            String eventId = response.assistantMessageId() + ":delta:" + index;
            events.add(new EventToSend(eventId, "message.delta",
                    new MessageDeltaEvent(eventId, conversationId,
                            response.assistantMessageId(), index, chunks.get(index), Instant.now())));
        }
        String completedId = response.assistantMessageId() + ":completed";
        events.add(new EventToSend(completedId, "message.completed",
                new MessageCompletedEvent(completedId, conversationId,
                        response.assistantMessageId(), response.answer(), response.traceId(),
                        response.replayed(), Instant.now())));

        int startIndex = resumeIndex(events, lastEventId);
        for (int index = startIndex; index < events.size(); index++) {
            EventToSend event = events.get(index);
            send(emitter, SseEmitter.event().id(event.id()).name(event.name()).data(event.data()));
        }
    }

    private int resumeIndex(List<EventToSend> events, String lastEventId) {
        if (lastEventId == null) {
            return 0;
        }
        for (int index = 0; index < events.size(); index++) {
            if (events.get(index).id().equals(lastEventId)) {
                return index + 1;
            }
        }
        return 0;
    }

    private void sendError(SseEmitter emitter,
                           String conversationId,
                           String code,
                           String message,
                           String traceId) {
        String eventId = traceId + ":error";
        try {
            send(emitter, SseEmitter.event().id(eventId).name("error")
                    .data(new StreamErrorEvent(eventId, conversationId, code,
                            message, traceId, Instant.now())));
            emitter.complete();
        } catch (RuntimeException ignored) {
            emitter.completeWithError(ignored);
        }
    }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException ex) {
            throw new StreamDisconnectedException(ex);
        }
    }

    private List<String> splitByCodePoints(String text, int chunkSize) {
        if (text == null || text.isEmpty()) {
            return List.of("");
        }
        List<String> chunks = new ArrayList<>();
        int codePointCount = text.codePointCount(0, text.length());
        for (int start = 0; start < codePointCount; start += chunkSize) {
            int end = Math.min(codePointCount, start + chunkSize);
            int startOffset = text.offsetByCodePoints(0, start);
            int endOffset = text.offsetByCodePoints(0, end);
            chunks.add(text.substring(startOffset, endOffset));
        }
        return chunks;
    }

    private String validLastEventId(String supplied) {
        if (supplied != null && supplied.matches("[A-Za-z0-9._:-]{1,160}")) {
            return supplied;
        }
        return null;
    }

    private record EventToSend(String id, String name, Object data) {
    }

    private static final class StreamDisconnectedException extends RuntimeException {
        private StreamDisconnectedException(IOException cause) {
            super(cause);
        }
    }
}
