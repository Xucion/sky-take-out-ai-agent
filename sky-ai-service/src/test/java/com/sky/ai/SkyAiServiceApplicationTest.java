package com.sky.ai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 验证 AI 服务能在本地 Fake Provider 模式下启动。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.flyway.enabled=false")
class SkyAiServiceApplicationTest {

    /**
     * 验证启动过程不依赖外部模型密钥、数据库或 Redis。
     */
    @Test
    void startsWithFakeProviderAndWithoutExternalModelKey() {
        // 轻量启动测试不连接模型、数据库或 Redis；数据库迁移由隔离的容器测试覆盖。
    }
}
