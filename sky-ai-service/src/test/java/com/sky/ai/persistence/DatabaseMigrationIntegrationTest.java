package com.sky.ai.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import com.sky.ai.tool.ShopStatus;
import com.sky.ai.tool.OrderProgress;
import com.sky.ai.tool.SkyServerToolClient;
import com.sky.ai.tool.ToolError;
import com.sky.ai.tool.ToolResponse;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class DatabaseMigrationIntegrationTest {

    private static final String USER_JWT_SECRET = "itheima";

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.45")
            .withDatabaseName("sky_ai_test")
            .withUsername("sky_ai")
            .withPassword("sky_ai_test_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.baseline-on-migrate", () -> false);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ToolCallRepository toolCallRepository;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private SkyServerToolClient toolClient;

    private MockMvc mockMvc;

    @BeforeEach
    void clearAiData() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        jdbcTemplate.update("DELETE FROM ai_tool_call");
        jdbcTemplate.update("DELETE FROM ai_message WHERE reply_to_message_id IS NOT NULL");
        jdbcTemplate.update("DELETE FROM ai_message");
        jdbcTemplate.update("DELETE FROM ai_conversation");
    }

    @Test
    void migratesAiTablesAndDoesNotRepeatAppliedMigration() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN ('ai_conversation', 'ai_message', 'ai_tool_call')
                """, Integer.class);
        Integer foreignKeyCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.referential_constraints
                WHERE constraint_schema = DATABASE()
                  AND constraint_name IN (
                      'fk_ai_message_conversation',
                      'fk_ai_message_reply_to_message',
                      'fk_ai_tool_call_conversation',
                      'fk_ai_tool_call_message')
                """, Integer.class);
        Integer appliedMigrationCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version IN ('1', '2') AND success = TRUE
                """, Integer.class);

        assertThat(tableCount).isEqualTo(3);
        assertThat(foreignKeyCount).isEqualTo(4);
        assertThat(appliedMigrationCount).isEqualTo(2);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void enforcesConversationOwnershipInQueriesAndMessageWrites() {
        AiConversation conversation = conversationRepository.create(101L, "WEB", "订单咨询");

        assertThat(conversationRepository.findOwnedById(conversation.conversationId(), 101L))
                .isPresent();
        assertThat(conversationRepository.findOwnedById(conversation.conversationId(), 202L))
                .isEmpty();
        assertThat(messageRepository.findHistoryOwnedByUser(
                conversation.conversationId(), 202L, 20, 0)).isEmpty();

        assertThatThrownBy(() -> messageRepository.append(202L,
                NewMessage.completedUserMessage(conversation.conversationId(),
                        "查询订单", "request-cross-user", "trace-cross-user")))
                .isInstanceOfSatisfying(com.sky.ai.error.AiServiceException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("CONVERSATION_NOT_FOUND"));
    }

    @Test
    void appendsMessagesIdempotentlyAndUpdatesAssistantWithOptimisticLock() {
        AiConversation conversation = conversationRepository.create(101L, "WEB", null);
        NewMessage request = NewMessage.completedUserMessage(
                conversation.conversationId(), "门店营业吗", "request-1", "trace-1");

        AiMessage first = messageRepository.append(101L, request);
        AiMessage duplicate = messageRepository.append(101L, request);
        AiMessage assistant = messageRepository.append(101L,
                NewMessage.pendingAssistantMessage(conversation.conversationId(), "trace-1"));

        assertThat(duplicate.messageId()).isEqualTo(first.messageId());
        assertThat(first.sequenceNo()).isEqualTo(1);
        assertThat(assistant.sequenceNo()).isEqualTo(2);
        assertThat(messageRepository.findHistoryOwnedByUser(
                conversation.conversationId(), 101L, 20, 0)).hasSize(2);

        MessageOutcome outcome = new MessageOutcome("门店当前营业中。",
                AiMessage.Status.COMPLETED, "fake", "fake", 10, 6, 25, null);
        assertThat(messageRepository.updateOutcome(
                assistant.messageId(), 202L, assistant.version(), outcome)).isFalse();
        assertThat(messageRepository.updateOutcome(
                assistant.messageId(), 101L, assistant.version(), outcome)).isTrue();
        assertThat(messageRepository.updateOutcome(
                assistant.messageId(), 101L, assistant.version(), outcome)).isFalse();

        AiMessage completed = messageRepository.findOwnedById(
                assistant.messageId(), 101L).orElseThrow();
        assertThat(completed.status()).isEqualTo(AiMessage.Status.COMPLETED);
        assertThat(completed.content()).isEqualTo("门店当前营业中。");
        assertThat(completed.version()).isEqualTo(1);
    }

    @Test
    void allocatesUniqueOrderedSequencesForConcurrentMessages() throws Exception {
        AiConversation conversation = conversationRepository.create(101L, "WEB", null);
        int messageCount = 6;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(messageCount);
        List<Future<AiMessage>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < messageCount; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    start.await();
                    return messageRepository.append(101L,
                            NewMessage.completedUserMessage(conversation.conversationId(),
                                    "message-" + index, "request-" + index, "trace-" + index));
                }));
            }
            start.countDown();

            List<Long> sequences = new ArrayList<>();
            for (Future<AiMessage> future : futures) {
                sequences.add(future.get().sequenceNo());
            }
            assertThat(sequences).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L);
            assertThat(messageRepository.findHistoryOwnedByUser(
                    conversation.conversationId(), 101L, 20, 0))
                    .extracting(AiMessage::sequenceNo)
                    .containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void auditsToolCallsAndOnlyAllowsOneTerminalTransition() {
        AiConversation conversation = conversationRepository.create(101L, "WEB", null);
        AiMessage assistant = messageRepository.append(101L,
                NewMessage.pendingAssistantMessage(conversation.conversationId(), "trace-tool"));

        AiToolCall started = toolCallRepository.start(101L, new NewToolCall(
                conversation.conversationId(), assistant.messageId(), "get_order_progress",
                Map.of("orderId", 9001L), null, "trace-tool"));

        assertThat(started.resultStatus()).isEqualTo(AiToolCall.Status.STARTED);
        assertThat(started.parameterSummary()).contains("9001");
        assertThat(toolCallRepository.findOwnedById(started.toolCallId(), 202L)).isEmpty();

        ToolCallOutcome outcome = new ToolCallOutcome(
                AiToolCall.Status.SUCCEEDED, "OK", "status=DELIVERING", 18);
        assertThat(toolCallRepository.finish(
                started.toolCallId(), 202L, started.version(), outcome)).isFalse();
        assertThat(toolCallRepository.finish(
                started.toolCallId(), 101L, started.version(), outcome)).isTrue();
        assertThat(toolCallRepository.finish(
                started.toolCallId(), 101L, started.version(), outcome)).isFalse();

        AiToolCall completed = toolCallRepository.findOwnedById(
                started.toolCallId(), 101L).orElseThrow();
        assertThat(completed.resultStatus()).isEqualTo(AiToolCall.Status.SUCCEEDED);
        assertThat(completed.resultCode()).isEqualTo("OK");
        assertThat(completed.version()).isEqualTo(1);
    }

    @Test
    void rejectsSensitiveKeysFromToolParameterSummary() {
        AiConversation conversation = conversationRepository.create(101L, "WEB", null);

        assertThatThrownBy(() -> toolCallRepository.start(101L, new NewToolCall(
                conversation.conversationId(), null, "get_shop_status",
                Map.of("authorizationToken", "must-not-be-saved"), null, "trace-sensitive")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("forbidden key");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_tool_call", Integer.class)).isZero();
    }

    @Test
    void conversationApisEnforceOwnershipAndReturnCursorHistory() throws Exception {
        String user101Token = userToken(101L);
        String user202Token = userToken(202L);

        mockMvc.perform(post("/api/ai/conversations")
                        .header("authentication", user101Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"订单咨询\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("订单咨询"))
                .andExpect(jsonPath("$.status").value("BOT_ACTIVE"))
                .andExpect(jsonPath("$.currentHandler").value("BOT"));

        AiConversation conversation = conversationRepository
                .findRecentOwnedByUser(101L, 10, 0).getFirst();
        messageRepository.append(101L, NewMessage.completedUserMessage(
                conversation.conversationId(), "订单到哪里了", "api-request-1", "api-trace-1"));
        messageRepository.append(101L, NewMessage.pendingAssistantMessage(
                conversation.conversationId(), "api-trace-1"));

        mockMvc.perform(get("/api/ai/conversations")
                        .header("authentication", user101Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].conversationId")
                        .value(conversation.conversationId()));

        mockMvc.perform(get("/api/ai/conversations/{id}", conversation.conversationId())
                        .header("authentication", user202Token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));

        mockMvc.perform(get("/api/ai/conversations/{id}/messages",
                        conversation.conversationId())
                        .header("authentication", user101Token)
                        .param("afterSequence", "0")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].sequenceNo").value(1))
                .andExpect(jsonPath("$.nextAfterSequence").value(1))
                .andExpect(jsonPath("$.hasMore").value(true));

        mockMvc.perform(get("/api/ai/conversations/{id}/messages",
                        conversation.conversationId())
                        .header("authentication", user101Token)
                        .param("afterSequence", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].sequenceNo").value(2))
                .andExpect(jsonPath("$.hasMore").value(false));

        mockMvc.perform(get("/api/ai/conversations/{id}/messages",
                        conversation.conversationId())
                        .header("authentication", user202Token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));
    }

    @Test
    void conversationApisRejectMissingAuthenticationAndInvalidPagination() throws Exception {
        mockMvc.perform(get("/api/ai/conversations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mockMvc.perform(get("/api/ai/conversations")
                        .header("authentication", userToken(101L))
                        .param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void chatPersistsMessagesAndToolAuditAndReplaysIdempotentRequest() throws Exception {
        AiConversation conversation = conversationRepository.create(101L, "WEB", "营业咨询");
        when(toolClient.getShopStatus(any(), anyString(), anyString())).thenReturn(
                new ToolResponse<>(true, new ShopStatus("OPEN", "营业中", null),
                        null, "downstream-trace"));
        String body = """
                {"conversationId":"%s","message":"门店现在营业吗？", "clientRequestId":"chat-1"}
                """.formatted(conversation.conversationId());

        String firstResponse = mockMvc.perform(post("/api/ai/poc/chat")
                        .header("authentication", userToken(101L))
                        .header("X-Trace-Id", "chat-trace-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("门店目前正在营业，可以正常下单。"))
                .andExpect(jsonPath("$.toolUsed").value("get_shop_status"))
                .andExpect(jsonPath("$.replayed").value(false))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/api/ai/poc/chat")
                        .header("authentication", userToken(101L))
                        .header("X-Trace-Id", "chat-trace-retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("门店目前正在营业，可以正常下单。"))
                .andExpect(jsonPath("$.replayed").value(true));

        List<AiMessage> messages = messageRepository.findHistoryOwnedByUser(
                conversation.conversationId(), 101L, 20, 0);
        List<AiToolCall> toolCalls = toolCallRepository.findOwnedByConversation(
                conversation.conversationId(), 101L, 20);
        assertThat(firstResponse).contains("userMessageId", "assistantMessageId");
        assertThat(messages).hasSize(2);
        assertThat(messages).extracting(AiMessage::status)
                .containsExactly(AiMessage.Status.COMPLETED, AiMessage.Status.COMPLETED);
        assertThat(messages.get(1).replyToMessageId()).isEqualTo(messages.get(0).messageId());
        assertThat(toolCalls).hasSize(1);
        assertThat(toolCalls.getFirst().resultStatus()).isEqualTo(AiToolCall.Status.SUCCEEDED);
        assertThat(toolCalls.getFirst().resultSummary()).isEqualTo("status=OPEN");
        verify(toolClient, times(1)).getShopStatus(any(), anyString(), anyString());
    }

    @Test
    void orderContextIsReusedOnlyInsideOwningConversation() throws Exception {
        AiConversation conversation = conversationRepository.create(101L, "WEB", "订单上下文");
        AiConversation anotherConversation = conversationRepository.create(101L, "WEB", "新会话");
        OrderProgress progress = new OrderProgress(19L, "DELIVERY_IN_PROGRESS", "配送中",
                null, null, List.of("VIEW_DETAIL"), false, "/orders/19");
        when(toolClient.getOrderProgress(eq(19L), any(), anyString(), anyString()))
                .thenReturn(new ToolResponse<>(true, progress, null, "order-context-trace"));

        String first = """
                {"conversationId":"%s","message":"查询订单id为19的进度", "clientRequestId":"ctx-1"}
                """.formatted(conversation.conversationId());
        mockMvc.perform(post("/api/ai/poc/chat")
                        .header("authentication", userToken(101L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolUsed").value("get_order_progress"));

        assertThat(conversationRepository.findOwnedById(
                conversation.conversationId(), 101L).orElseThrow().relatedOrderId())
                .isEqualTo(19L);

        String followUp = """
                {"conversationId":"%s","message":"它到哪了？", "clientRequestId":"ctx-2"}
                """.formatted(conversation.conversationId());
        mockMvc.perform(post("/api/ai/poc/chat")
                        .header("authentication", userToken(101L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(followUp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("ORDER_PROGRESS"))
                .andExpect(jsonPath("$.toolUsed").value("get_order_progress"));

        String isolated = """
                {"conversationId":"%s","message":"它到哪了？", "clientRequestId":"ctx-3"}
                """.formatted(anotherConversation.conversationId());
        mockMvc.perform(post("/api/ai/poc/chat")
                        .header("authentication", userToken(101L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(isolated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("ORDER_PROGRESS"))
                .andExpect(jsonPath("$.provider").value("policy"));

        assertThat(conversationRepository.findOwnedById(
                anotherConversation.conversationId(), 101L).orElseThrow().relatedOrderId())
                .isNull();
        verify(toolClient, times(2)).getOrderProgress(
                eq(19L), any(), anyString(), anyString());
    }

    @Test
    void chatRejectsIdempotencyConflictAndCrossUserConversation() throws Exception {
        AiConversation conversation = conversationRepository.create(101L, "WEB", null);
        String first = """
                {"conversationId":"%s","message":"查询订单进度", "clientRequestId":"same-key"}
                """.formatted(conversation.conversationId());
        String conflict = """
                {"conversationId":"%s","message":"查询另一个问题", "clientRequestId":"same-key"}
                """.formatted(conversation.conversationId());

        mockMvc.perform(post("/api/ai/poc/chat")
                        .header("authentication", userToken(101L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(first))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/ai/poc/chat")
                        .header("authentication", userToken(101L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conflict))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

        mockMvc.perform(post("/api/ai/poc/chat")
                        .header("authentication", userToken(202L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(first.replace("same-key", "cross-user-key")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));

        assertThat(messageRepository.findHistoryOwnedByUser(
                conversation.conversationId(), 101L, 20, 0)).hasSize(2);
    }

    @Test
    void chatPersistsFailedToolAndAssistantOutcome() throws Exception {
        AiConversation conversation = conversationRepository.create(101L, "WEB", null);
        when(toolClient.getShopStatus(any(), anyString(), anyString())).thenReturn(
                new ToolResponse<>(false, null,
                        new ToolError("SHOP_STATUS_UNAVAILABLE", "暂时无法查询", true),
                        "downstream-trace"));
        String body = """
                {"conversationId":"%s","message":"门店营业吗？", "clientRequestId":"failed-chat"}
                """.formatted(conversation.conversationId());

        mockMvc.perform(post("/api/ai/poc/chat")
                        .header("authentication", userToken(101L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SHOP_STATUS_UNAVAILABLE"));

        List<AiMessage> messages = messageRepository.findHistoryOwnedByUser(
                conversation.conversationId(), 101L, 20, 0);
        List<AiToolCall> toolCalls = toolCallRepository.findOwnedByConversation(
                conversation.conversationId(), 101L, 20);
        assertThat(messages).hasSize(2);
        assertThat(messages.getLast().status()).isEqualTo(AiMessage.Status.FAILED);
        assertThat(messages.getLast().errorCode()).isEqualTo("SHOP_STATUS_UNAVAILABLE");
        assertThat(toolCalls).hasSize(1);
        assertThat(toolCalls.getFirst().resultStatus()).isEqualTo(AiToolCall.Status.FAILED);
        assertThat(toolCalls.getFirst().resultCode()).isEqualTo("SHOP_STATUS_UNAVAILABLE");
    }

    @Test
    void sseStreamsStableEventsAndResumesWithoutDuplicateMessages() throws Exception {
        AiConversation conversation = conversationRepository.create(101L, "WEB", null);
        String body = """
                {"message":"你能提供哪些帮助？", "clientRequestId":"sse-chat-1"}
                """;

        MvcResult first = mockMvc.perform(post(
                        "/api/ai/conversations/{id}/messages/stream", conversation.conversationId())
                        .header("authentication", userToken(101L))
                        .header("X-Trace-Id", "sse-trace-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(first))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("event:message.delta")))
                .andExpect(content().string(containsString("event:message.completed")));

        List<AiMessage> firstMessages = messageRepository.findHistoryOwnedByUser(
                conversation.conversationId(), 101L, 20, 0);
        assertThat(firstMessages).hasSize(2);
        String assistantMessageId = firstMessages.getLast().messageId();
        String firstDeltaId = assistantMessageId + ":delta:0";

        MvcResult resumed = mockMvc.perform(post(
                        "/api/ai/conversations/{id}/messages/stream", conversation.conversationId())
                        .header("authentication", userToken(101L))
                        .header("Last-Event-ID", firstDeltaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(resumed))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("id:" + firstDeltaId + "\n"))))
                .andExpect(content().string(containsString(
                        "id:" + assistantMessageId + ":completed")))
                .andExpect(content().string(containsString("\"replayed\":true")));

        assertThat(messageRepository.findHistoryOwnedByUser(
                conversation.conversationId(), 101L, 20, 0)).hasSize(2);
    }

    @Test
    void sseRejectsCrossUserBeforeStreamingAndEmitsSafeToolError() throws Exception {
        AiConversation conversation = conversationRepository.create(101L, "WEB", null);
        String body = """
                {"message":"门店营业吗？", "clientRequestId":"sse-failed-chat"}
                """;

        mockMvc.perform(post(
                        "/api/ai/conversations/{id}/messages/stream", conversation.conversationId())
                        .header("authentication", userToken(202L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));

        when(toolClient.getShopStatus(any(), anyString(), anyString())).thenReturn(
                new ToolResponse<>(false, null,
                        new ToolError("SHOP_STATUS_UNAVAILABLE", "暂时无法查询", true),
                        "downstream-trace"));
        MvcResult failed = mockMvc.perform(post(
                        "/api/ai/conversations/{id}/messages/stream", conversation.conversationId())
                        .header("authentication", userToken(101L))
                        .header("X-Trace-Id", "sse-error-trace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(failed))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:error")))
                .andExpect(content().string(containsString("SHOP_STATUS_UNAVAILABLE")))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    private String userToken(long userId) {
        String payload = "{\"userId\":" + userId + ",\"exp\":"
                + (Instant.now().getEpochSecond() + 300) + "}";
        try {
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            String header = encoder.encodeToString(
                    "{\"alg\":\"HS256\",\"typ\":\"JWT\"}"
                            .getBytes(StandardCharsets.UTF_8));
            String body = encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    USER_JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = encoder.encodeToString(
                    mac.doFinal((header + "." + body).getBytes(StandardCharsets.UTF_8)));
            return header + "." + body + "." + signature;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
