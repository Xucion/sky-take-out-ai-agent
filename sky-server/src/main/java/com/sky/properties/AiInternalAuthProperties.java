package com.sky.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 服务调用 sky-server 时使用的内部认证配置。
 * 两个密钥必须通过环境变量等外部方式提供，不能提交真实密钥到仓库。
 */
@Component
@ConfigurationProperties(prefix = "sky.ai-internal")
@Data
public class AiInternalAuthProperties {

    /** sky-ai-service 签发服务身份 JWT 时使用的 HS256 密钥。 */
    private String serviceSecretKey;

    /** sky-ai-service 签发短期用户上下文 JWT 时使用的独立 HS256 密钥。 */
    private String userContextSecretKey;

    /** 服务身份令牌所在请求头，默认使用标准 Authorization 头。 */
    private String serviceTokenName = "Authorization";

    /** 用户上下文令牌所在请求头，与服务身份令牌分开传输和验证。 */
    private String userContextTokenName = "X-AI-User-Context";

    /** 服务 JWT 必须具有的 subject，防止其他内部服务复用同一入口。 */
    private String serviceSubject = "sky-ai-service";

    /** 两类 JWT 都必须匹配的 audience，防止发给其他服务的令牌被挪用。 */
    private String audience = "sky-server";

    /** 服务身份令牌允许的最大生命周期，默认 10 分钟。 */
    private long serviceTokenMaxTtlMillis = 600_000;

    /** 用户上下文允许的最大生命周期，默认 2 分钟，降低截获后的重放窗口。 */
    private long userContextMaxTtlMillis = 120_000;

    /** 接受的服务间时钟误差，避免轻微时钟漂移误伤合法请求。 */
    private long allowedClockSkewMillis = 30_000;
}
