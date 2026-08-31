package com.sky.controller.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.constant.JwtClaimsConstant;
import com.sky.interceptor.AiInternalAuthInterceptor;
import com.sky.properties.AiInternalAuthProperties;
import com.sky.service.AiOrderProgressService;
import com.sky.service.AiShopStatusService;
import com.sky.service.AiDishRecommendationService;
import com.sky.service.AiMealComboRecommendationService;
import com.sky.vo.ai.ShopStatusVO;
import com.sky.vo.ai.DishRecommendationItemVO;
import com.sky.vo.ai.DishRecommendationResultVO;
import com.sky.vo.ai.MealComboItemVO;
import com.sky.vo.ai.MealComboRecommendationResultVO;
import com.sky.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalAiToolControllerTest {

    private static final String SERVICE_SECRET = "service-secret-key-for-ai-tests-123456";
    private static final String USER_SECRET = "user-context-secret-for-ai-tests-123456";

    private AiOrderProgressService orderProgressService;
    private AiShopStatusService shopStatusService;
    private AiDishRecommendationService dishRecommendationService;
    private AiMealComboRecommendationService mealComboRecommendationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        orderProgressService = mock(AiOrderProgressService.class);
        shopStatusService = mock(AiShopStatusService.class);
        dishRecommendationService = mock(AiDishRecommendationService.class);
        mealComboRecommendationService = mock(AiMealComboRecommendationService.class);

        AiInternalAuthProperties properties = new AiInternalAuthProperties();
        properties.setServiceSecretKey(SERVICE_SECRET);
        properties.setUserContextSecretKey(USER_SECRET);

        AiInternalAuthInterceptor interceptor =
                new AiInternalAuthInterceptor(properties, new ObjectMapper());
        InternalAiToolController controller = new InternalAiToolController(
                orderProgressService, shopStatusService, dishRecommendationService,
                mealComboRecommendationService);

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
    void returnsAuthenticatedDishRecommendations() throws Exception {
        DishRecommendationItemVO item = DishRecommendationItemVO.builder()
                .dishId(101L).name("宫保鸡丁").price(new BigDecimal("28"))
                .matchedTags(List.of("下饭"))
                .reasonCodes(List.of("PRICE_MATCH", "TAG_MATCH"))
                .build();
        when(dishRecommendationService.recommend(any())).thenReturn(
                DishRecommendationResultVO.builder().items(List.of(item)).build());

        mockMvc.perform(post("/internal/ai-tools/catalog/dish-recommendations")
                        .contentType(APPLICATION_JSON)
                        .content("{\"maxPrice\":30,\"preferredTags\":[\"下饭\"],\"limit\":5}")
                        .header("Authorization", "Bearer " + serviceToken())
                        .header("X-AI-User-Context", userContextToken(1L, "conv-recommend")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].dishId").value(101))
                .andExpect(jsonPath("$.data.items[0].reasonCodes[1]").value("TAG_MATCH"));
    }

    /** 验证整餐组合接口返回人数、总价和各项数量。 */
    @Test
    void returnsAuthenticatedMealCombo() throws Exception {
        MealComboItemVO item = MealComboItemVO.builder().dishId(102L).name("香辣牛蛙")
                .unitPrice(new BigDecimal("68")).quantity(1).subtotal(new BigDecimal("68"))
                .role("MAIN").reasonCodes(List.of("SPICY_MATCH")).build();
        when(mealComboRecommendationService.recommend(any())).thenReturn(
                MealComboRecommendationResultVO.builder().items(List.of(item))
                        .totalPrice(new BigDecimal("68")).budget(new BigDecimal("200"))
                        .remainingBudget(new BigDecimal("132")).peopleCount(2).build());

        mockMvc.perform(post("/internal/ai-tools/catalog/meal-combinations")
                        .contentType(APPLICATION_JSON)
                        .content("{\"totalBudget\":200,\"peopleCount\":2,\"spicyLevelMin\":1}")
                        .header("Authorization", "Bearer " + serviceToken())
                        .header("X-AI-User-Context", userContextToken(1L, "conv-combo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.peopleCount").value(2))
                .andExpect(jsonPath("$.data.totalPrice").value(68))
                .andExpect(jsonPath("$.data.items[0].quantity").value(1));
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
