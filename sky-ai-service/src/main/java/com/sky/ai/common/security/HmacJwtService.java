package com.sky.ai.common.security;

import com.sky.ai.common.exception.AiServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * 项目只需要 HS256 的最小签发/验签能力，用 JDK 实现以避免让新服务依赖旧版 jjwt。
 * 这里不接受 alg=none，也不会根据信任边界外的 header 动态选择算法。
 */
@Component
public class HmacJwtService {

    // JWT 使用 Base64 URL 编码且去掉 padding，才能和 sky-server 现有 jjwt 解析器互通。
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    // 算法由服务端固定，不能读取外部 header 后再决定使用什么算法验签。
    private static final String HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    // Clock 单独注入是为了让过期时间相关测试可重复，而不是依赖测试执行时刻。
    private final Clock clock;
    private final ObjectMapper objectMapper;

    /**
     * 初始化 HmacJwtService，并注入其运行所需的依赖。
     */
    @Autowired
    public HmacJwtService(ObjectMapper objectMapper) {
        this(Clock.systemUTC(), objectMapper);
    }

    /**
     * 使用指定时钟创建 JWT 服务，便于对令牌有效期进行可重复测试。
     */
    HmacJwtService(Clock clock, ObjectMapper objectMapper) {
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    /**
     * 验证外部用户令牌并提取可信用户身份。
     */
    public UserIdentity verifyUserToken(String token, String secret) {
        // 先确认服务端配置完整；空密钥不能被解释为“跳过认证”。
        requireSecret(secret, "SKY_USER_JWT_SECRET");
        Map<String, Object> claims = verifyAndParse(token, secret);
        Object rawUserId = claims.get("userId");
        // userId 只能取自验签后的 claims，永远不接受对话 body 中的同名字段。
        if (!(rawUserId instanceof Number number) || number.longValue() <= 0) {
            throw unauthenticated();
        }
        return new UserIdentity(number.longValue());
    }

    /**
     * 签发短期内部服务身份令牌。
     */
    public String createServiceToken(String secret, Duration ttl) {
        requireSecret(secret, "SKY_AI_SERVICE_SECRET_KEY");
        long now = Instant.now(clock).getEpochSecond();
        // 服务令牌只表示“谁在调用”，不携带任何用户身份。
        String payload = "{" +
                "\"sub\":\"sky-ai-service\"," +
                "\"aud\":\"sky-server\"," +
                "\"tokenType\":\"service\"," +
                "\"iat\":" + now + "," +
                "\"exp\":" + (now + ttl.toSeconds()) + "," +
                "\"jti\":\"" + UUID.randomUUID() + "\"}";
        return sign(payload, secret);
    }

    /**
     * 签发绑定用户和会话的短期上下文令牌。
     */
    public String createUserContextToken(long userId, String conversationId,
                                         String secret, Duration ttl) {
        requireSecret(secret, "SKY_AI_USER_CONTEXT_SECRET_KEY");
        long now = Instant.now(clock).getEpochSecond();
        // 用户上下文使用另一把密钥，降低服务令牌泄露后被冒充用户令牌的风险。
        String payload = "{" +
                "\"aud\":\"sky-server\"," +
                "\"tokenType\":\"user_context\"," +
                "\"userId\":" + userId + "," +
                "\"conversationId\":" + quote(conversationId) + "," +
                "\"iat\":" + now + "," +
                "\"exp\":" + (now + ttl.toSeconds()) + "," +
                "\"jti\":\"" + UUID.randomUUID() + "\"}";
        return sign(payload, secret);
    }

    /**
     * 校验 JWT 签名、时效和声明后返回载荷。
     */
    private Map<String, Object> verifyAndParse(String token, String secret) {
        try {
            if (!StringUtils.hasText(token)) {
                throw unauthenticated();
            }
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) {
                throw unauthenticated();
            }
            // 签名覆盖原始 header.payload；不能先解码再重新序列化，否则字节会发生变化。
            byte[] expected = hmac(parts[0] + "." + parts[1], secret);
            byte[] supplied = URL_DECODER.decode(parts[2]);
            // 常量时间比较避免普通 equals 的提前返回暴露签名匹配前缀。
            if (!MessageDigest.isEqual(expected, supplied)) {
                throw unauthenticated();
            }
            String header = new String(URL_DECODER.decode(parts[0]), StandardCharsets.UTF_8);
            if (!header.contains("\"alg\":\"HS256\"")) {
                throw unauthenticated();
            }
            String json = new String(URL_DECODER.decode(parts[1]), StandardCharsets.UTF_8);
            Map<String, Object> claims = objectMapper.readValue(json, Map.class);
            Object expiration = claims.get("exp");
            // P0 至少强制验证 exp；内部令牌更严格的最大 TTL 仍由 sky-server 再校验一次。
            if (!(expiration instanceof Number number)
                    || number.longValue() <= Instant.now(clock).getEpochSecond()) {
                throw unauthenticated();
            }
            return claims;
        } catch (AiServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw unauthenticated();
        }
    }

    /**
     * 为 JWT 头部和载荷生成签名。
     */
    private String sign(String payload, String secret) {
        String headerPart = encode(HEADER.getBytes(StandardCharsets.UTF_8));
        String payloadPart = encode(payload.getBytes(StandardCharsets.UTF_8));
        return headerPart + "." + payloadPart + "." + encode(hmac(headerPart + "." + payloadPart, secret));
    }

    /**
     * 使用共享密钥计算 HMAC-SHA256。
     */
    private byte[] hmac(String input, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("HmacSHA256 unavailable", ex);
        }
    }

    /**
     * 执行无填充 Base64URL 编码。
     */
    private String encode(byte[] value) {
        return URL_ENCODER.encodeToString(value);
    }

    /**
     * 将文本安全编码为 JSON 字符串。
     */
    private String quote(String value) {
        // conversationId 会进入手工构造的 JSON，必须转义引号、反斜杠和换行。
        String safe = value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
        return "\"" + safe + "\"";
    }

    /**
     * 校验签名密钥满足最低安全要求。
     */
    private void requireSecret(String secret, String variableName) {
        if (!StringUtils.hasText(secret)) {
            throw new AiServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AUTH_NOT_CONFIGURED", "内部鉴权尚未配置：" + variableName);
        }
    }

    /**
     * 创建统一的未认证异常。
     */
    private AiServiceException unauthenticated() {
        return new AiServiceException(HttpStatus.UNAUTHORIZED,
                "UNAUTHENTICATED", "登录状态无效或已过期");
    }
}
