package com.sky.ai.agent.provider;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证模型工具的单次执行保护。
 */
class AiChatToolTest {

    /**
     * 验证重复执行工具时底层动作只会调用一次。
     */
    @Test
    void executesUnderlyingActionOnlyOnce() {
        AtomicInteger calls = new AtomicInteger();
        AiChatTool tool = new AiChatTool("safe_read", "只读测试工具",
                () -> "result-" + calls.incrementAndGet());

        assertEquals("result-1", tool.execute());
        assertEquals("result-1", tool.execute());
        assertEquals(1, calls.get());
    }
}
