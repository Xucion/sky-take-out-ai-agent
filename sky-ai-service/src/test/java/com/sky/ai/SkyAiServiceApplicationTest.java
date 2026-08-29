package com.sky.ai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.flyway.enabled=false")
class SkyAiServiceApplicationTest {

    @Test
    void startsWithFakeProviderAndWithoutExternalModelKey() {
        // 轻量启动测试不连接模型、数据库或 Redis；数据库迁移由隔离的容器测试覆盖。
    }
}
