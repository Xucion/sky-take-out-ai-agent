package com.sky.ai.chat;

import com.sky.ai.common.exception.AiServiceException;
import com.sky.ai.conversation.ConversationService;
import com.sky.ai.agent.AgentService;
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

/**
 * 提供支持幂等重放和断线续传的 SSE 聊天接口。
 */
@Validated
@RestController
@RequestMapping("/api/ai/conversations")
public class StreamingChatController {

    private static final long SSE_TIMEOUT_MILLIS = 60_000L;
    private static final int DELTA_CODE_POINTS = 24;

    private final ConversationService conversationService;
    private final AgentService agentService;
    private final Executor sseExecutor;

    /**
     * 初始化 StreamingChatController，并注入其运行所需的依赖。
     */
    public StreamingChatController(ConversationService conversationService,
                                   AgentService agentService,
                                   @Qualifier("sseExecutor") Executor sseExecutor) {
        this.conversationService = conversationService;
        this.agentService = agentService;
        this.sseExecutor = sseExecutor;
    }

    /**
     * 创建支持幂等重放和断线续传的 SSE 聊天连接。
     */
    @PostMapping(value = "/{conversationId}/messages/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestHeader(value = "authentication", required = false) String userToken,
            @RequestHeader(value = "X-Trace-Id", required = false) String suppliedTraceId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
            @PathVariable @NotBlank @Size(max = 64) String conversationId,
            @Valid @RequestBody ChatModels.StreamChatRequest request,
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

    /**
     * 在后台线程执行聊天并发送流式事件。
     */
    private void runStream(SseEmitter emitter,
                           String userToken,
                           String conversationId,
                           ChatModels.StreamChatRequest request,
                           String traceId,
                           String lastEventId) {
        try {
            ChatModels.ChatResponse response = agentService.chat(userToken,
                    new ChatModels.ChatRequest(conversationId, request.message(), request.orderId(),
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

    /**
     * 将完整回答切分为可恢复的 SSE 事件。
     */
    private void sendAnswerEvents(SseEmitter emitter,
                                  String conversationId,
                                  ChatModels.ChatResponse response,
                                  String lastEventId) {
        List<EventToSend> events = new ArrayList<>();
        List<String> chunks = splitByCodePoints(response.answer(), DELTA_CODE_POINTS);
        for (int index = 0; index < chunks.size(); index++) {
            String eventId = response.assistantMessageId() + ":delta:" + index;
            events.add(new EventToSend(eventId, "message.delta",
                    new ChatStreamEvent.MessageDelta(eventId, conversationId,
                            response.assistantMessageId(), index, chunks.get(index), Instant.now())));
        }
        String completedId = response.assistantMessageId() + ":completed";
        events.add(new EventToSend(completedId, "message.completed",
                new ChatStreamEvent.MessageCompleted(completedId, conversationId,
                        response.assistantMessageId(), response.answer(), response.traceId(),
                        response.replayed(), Instant.now())));

        int startIndex = resumeIndex(events, lastEventId);
        for (int index = startIndex; index < events.size(); index++) {
            EventToSend event = events.get(index);
            send(emitter, SseEmitter.event().id(event.id()).name(event.name()).data(event.data()));
        }
    }

    /**
     * 根据最后事件编号计算断线续传起点。
     */
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

    /**
     * 向客户端发送安全的流式错误事件。
     */
    private void sendError(SseEmitter emitter,
                           String conversationId,
                           String code,
                           String message,
                           String traceId) {
        String eventId = traceId + ":error";
        try {
            send(emitter, SseEmitter.event().id(eventId).name("error")
                    .data(new ChatStreamEvent.StreamError(eventId, conversationId, code,
                            message, traceId, Instant.now())));
            emitter.complete();
        } catch (RuntimeException ignored) {
            emitter.completeWithError(ignored);
        }
    }

    /**
     * 发送单个 SSE 事件并转换连接断开异常。
     */
    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException ex) {
            throw new StreamDisconnectedException(ex);
        }
    }

    /**
     * 按 Unicode 码点安全切分回答文本。
     */
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

    /**
     * 校验并规范化客户端提供的最后事件编号。
     */
    private String validLastEventId(String supplied) {
        if (supplied != null && supplied.matches("[A-Za-z0-9._:-]{1,160}")) {
            return supplied;
        }
        return null;
    }

    /**
     * 表示等待写入 SSE 连接的单个事件。
     */
    private record EventToSend(String id, String name, Object data) {
    }

    /**
     * StreamDisconnectedException 表示服务处理过程中可识别的异常。
     */
    private static final class StreamDisconnectedException extends RuntimeException {
        /**
         * 使用底层 I/O 异常创建流连接断开异常。
         */
        private StreamDisconnectedException(IOException cause) {
            super(cause);
        }
    }
}
