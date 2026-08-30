package com.sky.ai.common.security;

import com.sky.ai.common.exception.AiServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证 HMAC JWT 的验签、篡改检测和内部令牌声明。
 */
class HmacJwtServiceTest {

    private static final String SECRET = "a-test-secret-with-at-least-32-bytes";
    private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");

    private ObjectMapper objectMapper;
    private HmacJwtService service;

    /**
     * 使用固定时钟初始化待测 JWT 服务。
     */
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new HmacJwtService(Clock.fixed(NOW, ZoneOffset.UTC), objectMapper);
    }

    /**
     * 验证已有用户 JWT 并提取验签后的用户编号。
     */
    @Test
    void verifiesExistingUserJwtAndExtractsTrustedUserId() {
        String token = sign("{\"userId\":42,\"exp\":" + (NOW.getEpochSecond() + 60) + "}", SECRET);
        assertEquals(42L, service.verifyUserToken(token, SECRET).userId());
    }

    /**
     * 验证服务拒绝签名被篡改的用户 JWT。
     */
    @Test
    void rejectsTamperedUserJwt() {
        String token = sign("{\"userId\":42,\"exp\":" + (NOW.getEpochSecond() + 60) + "}", SECRET);
        assertThrows(AiServiceException.class,
                () -> service.verifyUserToken(token.substring(0, token.length() - 2) + "aa", SECRET));
    }

    /**
     * 验证内部令牌仅包含预期的服务边界声明。
     */
    @Test
    void internalTokensContainExpectedBoundaryClaims() throws Exception {
        String serviceToken = service.createServiceToken(SECRET, Duration.ofMinutes(5));
        Map<?, ?> serviceClaims = payload(serviceToken);
        assertEquals("sky-ai-service", serviceClaims.get("sub"));
        assertEquals("sky-server", serviceClaims.get("aud"));
        assertEquals("service", serviceClaims.get("tokenType"));

        String contextToken = service.createUserContextToken(
                42L, "conversation-1", SECRET, Duration.ofMinutes(2));
        Map<?, ?> contextClaims = payload(contextToken);
        assertEquals(42, ((Number) contextClaims.get("userId")).intValue());
        assertEquals("conversation-1", contextClaims.get("conversationId"));
        assertEquals("user_context", contextClaims.get("tokenType"));
    }

    /**
     * 解码 JWT 载荷供断言使用。
     */
    private Map<?, ?> payload(String token) throws Exception {
        String json = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        return objectMapper.readValue(json, Map.class);
    }

    /**
     * 为 JWT 头部和载荷生成签名。
     */
    private String sign(String payload, String secret) {
        try {
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            String header = encoder.encodeToString(
                    "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            String body = encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = encoder.encodeToString(
                    mac.doFinal((header + "." + body).getBytes(StandardCharsets.UTF_8)));
            return header + "." + body + "." + signature;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
