package com.sky.ai.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 验证追踪编号的保留和替换规则。
 */
class TraceIdsTest {

    /**
     * 验证格式安全的上游追踪编号会被保留。
     */
    @Test
    void keepsSafeUpstreamTraceId() {
        assertEquals("trace_123456", TraceIds.validOrRandom("trace_123456"));
    }

    /**
     * 验证格式不安全的追踪编号会被随机值替换。
     */
    @Test
    void replacesUnsafeTraceId() {
        assertNotEquals("bad\ntrace", TraceIds.validOrRandom("bad\ntrace"));
    }
}
