package com.sky.ai.model;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiChatToolTest {

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
