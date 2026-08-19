package com.sky.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.constant.JwtClaimsConstant;
import com.sky.context.AiRequestContext;
import com.sky.properties.AiInternalAuthProperties;
import com.sky.utils.JwtUtil;
import com.sky.vo.ai.AiToolResponse;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * AI 内部工具接口的信任边界。
 * 服务令牌证明调用方是 sky-ai-service，用户上下文令牌证明本次调用代表哪个已登录用户。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiInternalAuthInterceptor implements HandlerInterceptor {

    /*
     * tokenType 用于区分两类令牌。即使两个密钥被错误配置成相同值，
     * 服务令牌也不能直接冒充用户上下文令牌。
     */
    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String SERVICE_TOKEN_TYPE = "service";
    private static final String USER_CONTEXT_TOKEN_TYPE = "user_context";
    private static final String CONVERSATION_ID_CLAIM = "conversationId";

    private final AiInternalAuthProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        String traceId = getOrCreateTraceId(request);
        try {
            // 空密钥属于服务端配置故障，返回 503；不能退化为“跳过认证”。
            ensureSecretsConfigured();

            // 第一层验证调用服务身份，只接受 Authorization: Bearer <JWT>。
            Claims serviceClaims = parseBearerToken(request.getHeader(properties.getServiceTokenName()));
            validateServiceClaims(serviceClaims);

            // 第二层验证短期用户上下文。模型看不到密钥，也不能自行构造这个令牌。
            String userContextToken = request.getHeader(properties.getUserContextTokenName());
            if (!StringUtils.hasText(userContextToken)) {
                return reject(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "UNAUTHENTICATED", "缺少用户上下文", traceId);
            }

            Claims userClaims = JwtUtil.parseJWT(properties.getUserContextSecretKey(), userContextToken);
            validateUserClaims(userClaims);

            // 只有两个令牌都验证通过后，才把用户身份放入本次请求上下文。
            Long userId = Long.valueOf(userClaims.get(JwtClaimsConstant.USER_ID).toString());
            String conversationId = userClaims.get(CONVERSATION_ID_CLAIM).toString();
            AiRequestContext.set(userId, conversationId, traceId);
            return true;
        } catch (IllegalStateException ex) {
            // 配置故障与客户端认证失败分开，便于监控告警和故障定位。
            log.error("AI内部认证配置不可用，traceId={}", traceId, ex);
            return reject(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "TOOL_UNAVAILABLE", "AI内部认证暂不可用", traceId);
        } catch (Exception ex) {
            // 不把 JWT 解析细节写入响应，避免帮助攻击者逐步修正伪造令牌。
            log.warn("AI内部认证失败，traceId={}，原因={}", traceId, ex.getMessage());
            return reject(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "UNAUTHENTICATED", "AI内部调用认证失败", traceId);
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 无论 Controller 成功还是抛异常，都必须清理线程变量。
        AiRequestContext.clear();
    }

    private void ensureSecretsConfigured() {
        if (!StringUtils.hasText(properties.getServiceSecretKey())
                || !StringUtils.hasText(properties.getUserContextSecretKey())) {
            throw new IllegalStateException("AI internal authentication secrets are not configured");
        }
    }

    private Claims parseBearerToken(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Missing bearer token");
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("Empty bearer token");
        }
        return JwtUtil.parseJWT(properties.getServiceSecretKey(), token);
    }

    private void validateServiceClaims(Claims claims) {
        // subject 限定调用方，audience 限定接收方，tokenType 限定令牌用途。
        requireClaim(claims, Claims.SUBJECT, properties.getServiceSubject());
        requireClaim(claims, Claims.AUDIENCE, properties.getAudience());
        requireClaim(claims, TOKEN_TYPE_CLAIM, SERVICE_TOKEN_TYPE);
        validateTokenLifetime(claims, properties.getServiceTokenMaxTtlMillis());
    }

    private void validateUserClaims(Claims claims) {
        requireClaim(claims, Claims.AUDIENCE, properties.getAudience());
        requireClaim(claims, TOKEN_TYPE_CLAIM, USER_CONTEXT_TOKEN_TYPE);
        validateTokenLifetime(claims, properties.getUserContextMaxTtlMillis());

        Object userId = claims.get(JwtClaimsConstant.USER_ID);
        Object conversationId = claims.get(CONVERSATION_ID_CLAIM);
        if (userId == null || !StringUtils.hasText(conversationId == null ? null : conversationId.toString())) {
            throw new IllegalArgumentException("Incomplete user context");
        }

        long parsedUserId = Long.parseLong(userId.toString());
        // conversationId 只作为关联键使用，但仍要限长，避免异常大 Claim 污染日志和上下文。
        if (parsedUserId <= 0 || conversationId.toString().length() > 64) {
            throw new IllegalArgumentException("Invalid user context");
        }
    }

    private void requireClaim(Claims claims, String name, String expected) {
        Object actual = claims.get(name);
        if (actual == null || !expected.equals(actual.toString())) {
            throw new IllegalArgumentException("Invalid claim: " + name);
        }
    }

    private void validateTokenLifetime(Claims claims, long maxTtlMillis) {
        Date issuedAt = claims.getIssuedAt();
        Date expiration = claims.getExpiration();
        String tokenId = claims.getId();
        if (issuedAt == null || expiration == null || !StringUtils.hasText(tokenId)) {
            throw new IllegalArgumentException("Incomplete token lifetime claims");
        }

        long now = System.currentTimeMillis();
        // iat 明显晚于服务器时间通常代表签发方时钟异常或伪造令牌。
        if (issuedAt.getTime() > now + properties.getAllowedClockSkewMillis()) {
            throw new IllegalArgumentException("Token issued in the future: issuedAt="
                    + issuedAt.getTime() + ", now=" + now + ", skew="
                    + properties.getAllowedClockSkewMillis());
        }
        long ttl = expiration.getTime() - issuedAt.getTime();
        // 即使签名合法，超出最大有效期的令牌也不能作为短期内部身份使用。
        if (ttl <= 0 || ttl > maxTtlMillis) {
            throw new IllegalArgumentException("Token lifetime exceeds limit");
        }
    }

    private String getOrCreateTraceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        // 只接受有限字符集和长度的上游 Trace ID，避免日志注入。
        if (StringUtils.hasText(traceId) && traceId.matches("[A-Za-z0-9_-]{8,64}")) {
            return traceId;
        }
        return UUID.randomUUID().toString();
    }

    private boolean reject(HttpServletResponse response, int status, String code,
                           String message, String traceId) throws Exception {
        // 认证失败时也返回统一工具错误结构，让 AI 服务无需解析 HTML 或容器默认错误页。
        AiRequestContext.clear();
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                AiToolResponse.error(code, message, false, traceId));
        return false;
    }
}
