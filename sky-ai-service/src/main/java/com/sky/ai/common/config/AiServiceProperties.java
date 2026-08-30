package com.sky.ai.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * AI 服务自有配置。所有密钥只允许通过运行环境注入，不能提交到仓库。
 * 将 application.yml 或环境变量中的配置，自动映射到这个强类型的配置对象中。
 */
@ConfigurationProperties(prefix = "app")
public record AiServiceProperties(Ai ai, SkyServer skyServer, Auth auth) {

    /** provider 决定 Fake/Qwen 实现，maxInputChars 是部署侧可调的第二层输入限制。 */
    public record Ai(String provider, int maxInputChars) {
    }

    /** 只保存业务门面的地址和超时；AI 服务不配置交易数据库或 Redis 地址。 */
    public record SkyServer(String baseUrl, Duration connectTimeout, Duration readTimeout) {
    }

    /**
     * 三把密钥用途不同：验证外部用户、证明 AI 服务、签署内部用户上下文。
     * 即使开发环境也不应为了省事把 serviceJwtSecret 与 userContextJwtSecret 设成同一个值。
     */
    public record Auth(String userJwtSecret,
                       String serviceJwtSecret,
                       String userContextJwtSecret,
                       Duration serviceTokenTtl,
                       Duration userContextTokenTtl) {
    }
}
