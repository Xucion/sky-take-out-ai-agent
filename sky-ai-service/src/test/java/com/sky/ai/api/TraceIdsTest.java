package com.sky.ai.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TraceIdsTest {

    @Test
    void keepsSafeUpstreamTraceId() {
        assertEquals("trace_123456", TraceIds.validOrRandom("trace_123456"));
    }

    @Test
    void replacesUnsafeTraceId() {
        assertNotEquals("bad\ntrace", TraceIds.validOrRandom("bad\ntrace"));
    }
}
