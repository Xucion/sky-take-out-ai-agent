package com.sky.ai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SkyAiServiceApplicationTest {

    @Test
    void startsWithFakeProviderAndWithoutExternalModelKey() {
        // 默认配置必须允许开发者在没有模型 Key、数据库和 Redis 的情况下启动服务。
    }
}
