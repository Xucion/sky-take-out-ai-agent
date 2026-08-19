package com.sky.ai;

import com.sky.ai.config.AiServiceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AiServiceProperties.class)
public class SkyAiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkyAiServiceApplication.class, args);
    }
}
