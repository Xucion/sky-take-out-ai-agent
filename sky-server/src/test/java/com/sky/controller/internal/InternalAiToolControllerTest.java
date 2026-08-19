package com.sky.controller.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.constant.JwtClaimsConstant;
import com.sky.interceptor.AiInternalAuthInterceptor;
import com.sky.properties.AiInternalAuthProperties;
import com.sky.service.AiOrderProgressService;
import com.sky.service.AiShopStatusService;
import com.sky.vo.ai.ShopStatusVO;
import com.sky.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalAiToolControllerTest {

    private static final String SERVICE_SECRET = "service-secret-key-for-ai-tests-123456";
    private static final String USER_SECRET = "user-context-secret-for-ai-tests-123456";

    private AiOrderProgressService orderProgressService;
    private AiShopStatusService shopStatusService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        orderProgressService = mock(AiOrderProgressService.class);
        shopStatusService = mock(AiShopStatusService.class);

        AiInternalAuthProperties properties = new AiInternalAuthProperties();
        properties.setServiceSecretKey(SERVICE_SECRET);
        properties.setUserContextSecretKey(USER_SECRET);

        AiInternalAuthInterceptor interceptor =
                new AiInternalAuthInterceptor(properties, new ObjectMapper());
        InternalAiToolController controller = new InternalAiToolController(orderProgressService, shopStatusService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(interceptor)
                .build();
    }

    @Test
    void returnsAuthenticatedShopStatus() throws Exception {
        when(shopStatusService.getShopStatus()).thenReturn(
                ShopStatusVO.builder().status("OPEN").statusText("营业中").build());

        mockMvc.perform(get("/internal/ai-tools/shop/status")
                        .header("Authorization", "Bearer " + serviceToken())
                        .header("X-AI-User-Context", userContextToken(1L, "conv-shop")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("OPEN"));
    }

    @Test
    void authenticatedUserCannotReadAnotherUsersOrder() throws Exception {
        // U1 请求属于 U2 的订单时，对外统一表现为当前账号下找不到该订单。
        when(orderProgressService.getOrderProgress(201L, 1L)).thenReturn(null);

        mockMvc.perform(get("/internal/ai-tools/users/me/orders/201/progress")
                        .header("Authorization", "Bearer " + serviceToken())
                        .header("X-AI-User-Context", userContextToken(1L, "conv-u1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(orderProgressService).getOrderProgress(201L, 1L);
    }

    @Test
    void rejectsMissingServiceToken() throws Exception {
        mockMvc.perform(get("/internal/ai-tools/users/me/orders/101/progress")
                        .header("X-AI-User-Context", userContextToken(1L, "conv-u1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void rejectsUserContextWithWrongAudience() throws Exception {
        mockMvc.perform(get("/internal/ai-tools/users/me/orders/101/progress")
                        .header("Authorization", "Bearer " + serviceToken())
                        .header("X-AI-User-Context", userContextToken(1L, "conv-u1", "other-service")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    private String serviceToken() {
        Map<String, Object> claims = new HashMap<>();
        claims.put(Claims.SUBJECT, "sky-ai-service");
        claims.put(Claims.AUDIENCE, "sky-server");
        claims.put(Claims.ISSUED_AT, System.currentTimeMillis() / 1000);
        claims.put(Claims.ID, UUID.randomUUID().toString());
        claims.put("tokenType", "service");
        return JwtUtil.createJWT(SERVICE_SECRET, 60_000, claims);
    }

    private String userContextToken(Long userId, String conversationId) {
        return userContextToken(userId, conversationId, "sky-server");
    }

    private String userContextToken(Long userId, String conversationId, String audience) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(Claims.AUDIENCE, audience);
        claims.put(Claims.ISSUED_AT, System.currentTimeMillis() / 1000);
        claims.put(Claims.ID, UUID.randomUUID().toString());
        claims.put("tokenType", "user_context");
        claims.put(JwtClaimsConstant.USER_ID, userId);
        claims.put("conversationId", conversationId);
        return JwtUtil.createJWT(USER_SECRET, 60_000, claims);
    }
}
