package com.sky.ai;

import com.sky.ai.common.config.AiServiceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * AI 客服独立服务的 Spring Boot 启动入口。
 */
@SpringBootApplication
@EnableConfigurationProperties(AiServiceProperties.class)
public class SkyAiServiceApplication {

    /**
     * 启动 AI 客服 Spring Boot 应用。
     */
    public static void main(String[] args) {
        SpringApplication.run(SkyAiServiceApplication.class, args);
    }
}
