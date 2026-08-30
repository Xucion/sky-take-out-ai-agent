package com.sky.ai.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * StreamingConfiguration 提供对应基础设施的 Spring Bean 配置。
 */
@Configuration
public class StreamingConfiguration {

    /**
     * 创建用于 SSE 后台任务的虚拟线程执行器。
     */
    @Bean(name = "sseExecutor", destroyMethod = "close")
    ExecutorService sseExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
