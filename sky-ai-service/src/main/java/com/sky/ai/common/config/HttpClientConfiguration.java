package com.sky.ai.common.config;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 统一设置到业务服务的连接与读取超时，防止一次工具调用长期占用对话线程。
 */
@Configuration
public class HttpClientConfiguration {

    /**
     * 创建访问业务服务的 RestClient。
     */
    @Bean
    RestClient skyServerRestClient(RestClient.Builder builder, AiServiceProperties properties) {
        AiServiceProperties.SkyServer server = properties.skyServer();
        // 复用 Boot 管理的 Builder，保留统一的消息转换器、观测和后续代理扩展能力。
        return builder
                .baseUrl(server.baseUrl())
                // 连接超时限制建连阶段，读取超时限制下游已连接但迟迟不返回的情况。
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(
                        HttpClientSettings.defaults().withTimeouts(
                                server.connectTimeout(), server.readTimeout())))
                .build();
    }
}
