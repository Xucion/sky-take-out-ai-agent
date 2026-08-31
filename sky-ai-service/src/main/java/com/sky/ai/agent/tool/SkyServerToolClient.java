package com.sky.ai.agent.tool;

import com.sky.ai.common.exception.AiServiceException;
import com.sky.ai.common.security.IdentityBridgeService;
import com.sky.ai.common.security.InternalAuthTokens;
import com.sky.ai.common.security.UserIdentity;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * AI 服务访问业务数据的唯一出口。每次请求同时携带服务身份与短时用户上下文。
 */
@Component
public class SkyServerToolClient {

    // Java 泛型擦除后需要保留完整响应类型，否则 data 会被反序列化成无约束 Map。
    private static final ParameterizedTypeReference<ToolModels.ToolResponse<ToolModels.ShopStatus>> SHOP_TYPE =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ToolModels.ToolResponse<ToolModels.OrderProgress>> ORDER_TYPE =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ToolModels.ToolResponse<ToolModels.DishRecommendationResult>>
            RECOMMENDATION_TYPE = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ToolModels.ToolResponse<ToolModels.MealComboRecommendationResult>>
            MEAL_COMBO_TYPE = new ParameterizedTypeReference<>() { };

    private final RestClient restClient;
    private final IdentityBridgeService identityBridge;

    /**
     * 初始化 SkyServerToolClient，并注入其运行所需的依赖。
     */
    public SkyServerToolClient(RestClient skyServerRestClient, IdentityBridgeService identityBridge) {
        this.restClient = skyServerRestClient;
        this.identityBridge = identityBridge;
    }

    /**
     * 调用业务服务查询当前门店状态。
     */
    public ToolModels.ToolResponse<ToolModels.ShopStatus> getShopStatus(UserIdentity identity,
                                                   String conversationId,
                                                   String traceId) {
        // 门店状态虽然不是用户私有数据，仍坚持双令牌，避免出现未保护的内部接口旁路。
        InternalAuthTokens tokens = identityBridge.issueInternalTokens(identity, conversationId);
        return get("/internal/ai-tools/shop/status", tokens, traceId, SHOP_TYPE);
    }

    /**
     * 调用业务服务查询当前用户订单进度。
     */
    public ToolModels.ToolResponse<ToolModels.OrderProgress> getOrderProgress(long orderId,
                                                         UserIdentity identity,
                                                         String conversationId,
                                                         String traceId) {
        // 这里只传 orderId；userId 被封装在签名上下文中，调用者无法用 URL 参数替换。
        InternalAuthTokens tokens = identityBridge.issueInternalTokens(identity, conversationId);
        return get("/internal/ai-tools/users/me/orders/{orderId}/progress",
                tokens, traceId, ORDER_TYPE, orderId);
    }

    /**
     * 调用业务服务按结构化条件推荐当前可售菜品。
     */
    public ToolModels.ToolResponse<ToolModels.DishRecommendationResult> recommendDishes(
            ToolModels.DishRecommendationRequest request,
            UserIdentity identity,
            String conversationId,
            String traceId) {
        InternalAuthTokens tokens = identityBridge.issueInternalTokens(identity, conversationId);
        return post("/internal/ai-tools/catalog/dish-recommendations",
                request, tokens, traceId, RECOMMENDATION_TYPE);
    }

    /** 调用业务服务生成满足总预算和人数约束的整餐组合。 */
    public ToolModels.ToolResponse<ToolModels.MealComboRecommendationResult> recommendMealCombo(
            ToolModels.MealComboRecommendationRequest request,
            UserIdentity identity,
            String conversationId,
            String traceId) {
        InternalAuthTokens tokens = identityBridge.issueInternalTokens(identity, conversationId);
        return post("/internal/ai-tools/catalog/meal-combinations",
                request, tokens, traceId, MEAL_COMBO_TYPE);
    }

    /**
     * 携带内部身份令牌执行受控 GET 请求。
     */
    private <T> ToolModels.ToolResponse<T> get(String uri, InternalAuthTokens tokens, String traceId,
                                    ParameterizedTypeReference<ToolModels.ToolResponse<T>> type,
                                    Object... uriVariables) {
        try {
            ToolModels.ToolResponse<T> response = restClient.get()
                    .uri(uri, uriVariables)
                    // 两个 header 分别证明服务身份与“本次代表哪个登录用户”。
                    .header("Authorization", "Bearer " + tokens.serviceToken())
                    .header("X-AI-User-Context", tokens.userContextToken())
                    .header("X-Trace-Id", traceId)
                    .retrieve()
                    .body(type);
            if (response == null) {
                // 2xx 空 body 不是成功工具结果，不能交给模型自行猜测。
                throw unavailable();
            }
            return response;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                // sky-server 故意不区分“不存在”和“属于其他用户”，这里保持相同语义。
                throw new AiServiceException(HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND", "未找到当前账号下的该订单");
            }
            if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
                // 内部鉴权失败通常是部署配置问题，不应误导终端用户重新登录。
                throw new AiServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                        "INTERNAL_AUTH_FAILED", "客服内部鉴权失败，请联系管理员检查配置");
            }
            throw unavailable();
        } catch (RestClientException ex) {
            throw unavailable();
        }
    }

    /**
     * 携带内部身份令牌执行受控 POST 请求。
     */
    private <T> ToolModels.ToolResponse<T> post(String uri,
                                               Object body,
                                               InternalAuthTokens tokens,
                                               String traceId,
                                               ParameterizedTypeReference<ToolModels.ToolResponse<T>> type) {
        try {
            ToolModels.ToolResponse<T> response = restClient.post()
                    .uri(uri)
                    .header("Authorization", "Bearer " + tokens.serviceToken())
                    .header("X-AI-User-Context", tokens.userContextToken())
                    .header("X-Trace-Id", traceId)
                    .body(body)
                    .retrieve()
                    .body(type);
            if (response == null) {
                throw unavailable();
            }
            return response;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 400) {
                throw new AiServiceException(HttpStatus.BAD_REQUEST,
                        "INVALID_RECOMMENDATION_PREFERENCES", "菜品推荐条件不正确，请调整后重试");
            }
            if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
                throw new AiServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                        "INTERNAL_AUTH_FAILED", "客服内部鉴权失败，请联系管理员检查配置");
            }
            throw unavailable();
        } catch (RestClientException ex) {
            throw unavailable();
        }
    }

    /**
     * 创建下游业务服务不可用异常。
     */
    private AiServiceException unavailable() {
        return new AiServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                "TOOL_UNAVAILABLE", "业务信息暂时无法查询，请稍后重试");
    }
}
