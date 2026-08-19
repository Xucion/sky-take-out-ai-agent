package com.sky.ai.service;

import com.sky.ai.api.ChatRequest;
import com.sky.ai.api.ChatResponse;
import com.sky.ai.config.AiServiceProperties;
import com.sky.ai.model.AiChatProvider;
import com.sky.ai.security.IdentityBridgeService;
import com.sky.ai.security.UserIdentity;
import com.sky.ai.tool.OrderProgress;
import com.sky.ai.tool.ShopStatus;
import com.sky.ai.tool.SkyServerToolClient;
import com.sky.ai.tool.ToolResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerSupportAgentServiceTest {

    private IdentityBridgeService identityBridge;
    private SkyServerToolClient toolClient;
    private AiChatProvider provider;
    private CustomerSupportAgentService service;
    private final UserIdentity identity = new UserIdentity(7L);

    @BeforeEach
    void setUp() {
        identityBridge = mock(IdentityBridgeService.class);
        toolClient = mock(SkyServerToolClient.class);
        provider = mock(AiChatProvider.class);
        AiServiceProperties properties = new AiServiceProperties(
                new AiServiceProperties.Ai("fake", 2000),
                new AiServiceProperties.SkyServer("http://localhost:8080",
                        Duration.ofSeconds(2), Duration.ofSeconds(5)),
                new AiServiceProperties.Auth("user", "service", "context",
                        Duration.ofMinutes(5), Duration.ofMinutes(2)));
        service = new CustomerSupportAgentService(identityBridge, toolClient, provider, properties);
        when(identityBridge.verifyUser("user-token")).thenReturn(identity);
    }

    @Test
    void shopIntentCallsOnlyShopTool() {
        when(toolClient.getShopStatus(identity, "c1", "t1")).thenReturn(
                new ToolResponse<>(true, new ShopStatus("OPEN", "营业中", null), null, "t1"));
        when(provider.chat(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.contains("status=OPEN"))).thenReturn("营业中");

        ChatResponse response = service.chat("user-token",
                new ChatRequest("c1", "现在营业吗？", null), "t1");

        assertEquals("get_shop_status", response.toolUsed());
        assertEquals("营业中", response.answer());
        verify(toolClient, never()).getOrderProgress(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void orderIntentUsesAuthenticatedIdentityAndSpecifiedOrder() {
        OrderProgress progress = new OrderProgress(88L, "CONFIRMED", "商家已接单，正在准备",
                null, null, List.of("VIEW_DETAIL"), false, "/orders/88");
        when(toolClient.getOrderProgress(88L, identity, "c2", "t2")).thenReturn(
                new ToolResponse<>(true, progress, null, "t2"));
        when(provider.chat(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.contains("orderId=88"))).thenReturn("正在准备");

        ChatResponse response = service.chat("user-token",
                new ChatRequest("c2", "订单到哪了？", 88L), "t2");

        assertEquals("get_order_progress", response.toolUsed());
        verify(toolClient).getOrderProgress(88L, identity, "c2", "t2");
    }

    @Test
    void missingOrderIdDoesNotCallTool() {
        ChatResponse response = service.chat("user-token",
                new ChatRequest("c3", "查一下订单进度", null), "t3");

        assertEquals("ORDER_PROGRESS", response.intent());
        assertEquals(null, response.toolUsed());
        verify(toolClient, never()).getOrderProgress(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }
}
