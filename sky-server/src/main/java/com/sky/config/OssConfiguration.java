package com.sky.config;

import com.sky.properties.MinioProperties;
import com.sky.utils.MinioUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置类，用于创建MinioUtil对象
 */
@Configuration
@Slf4j
public class OssConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MinioUtil minioUtil(MinioProperties minioProperties) {
        log.info("开始创建 MinIO 文件上传工具类对象：{}", minioProperties);
        MinioUtil minioUtil = new MinioUtil(
                minioProperties.getEndpoint(),
                minioProperties.getAccessKey(),
                minioProperties.getSecretKey(),
                minioProperties.getBucketName());
        try {
            minioUtil.setPublicReadPolicy();
        } catch (IllegalStateException exception) {
            // 文件服务不可用不应阻止点餐、登录等核心接口启动。
            log.warn("MinIO 当前不可用，文件上传功能将暂时不可用：{}", exception.getMessage());
        }
        return minioUtil;
    }
}
