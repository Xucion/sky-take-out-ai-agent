package com.sky.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class StreamingConfiguration {

    @Bean(name = "sseExecutor", destroyMethod = "close")
    ExecutorService sseExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
