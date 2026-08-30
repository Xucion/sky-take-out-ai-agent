package com.sky.ai.chat;

import com.sky.ai.agent.AgentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 最小 PoC 接口。沿用现有用户端的 authentication 请求头，后续可切换标准网关鉴权。
 */
@RestController
@RequestMapping("/api/ai/poc")
public class ChatController {

    private final AgentService agentService;

    /**
     * 初始化 ChatController，并注入其运行所需的依赖。
     */
    public ChatController(AgentService agentService) {
        this.agentService = agentService;
    }

    /**
     * 接收 PoC 聊天请求，规范化追踪编号后交给 Agent 编排服务处理。
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatModels.ChatResponse> chat(
            @RequestHeader(value = "authentication", required = false) String userToken,
            @RequestHeader(value = "X-Trace-Id", required = false) String suppliedTraceId,
            @Valid @RequestBody ChatModels.ChatRequest request) {
        // 不可信的上游 Trace ID 会被替换，防止控制字符进入日志和内部请求头。
        String traceId = TraceIds.validOrRandom(suppliedTraceId);
        // Controller 不解析 JWT、不判断意图，只负责协议校验并交给应用服务。
        return ResponseEntity.ok(agentService.chat(userToken, request, traceId));
    }
}
