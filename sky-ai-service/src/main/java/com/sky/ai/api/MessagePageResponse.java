package com.sky.ai.api;

import java.util.List;

public record MessagePageResponse(
        List<MessageView> items,
        int limit,
        long afterSequence,
        long nextAfterSequence,
        boolean hasMore) {
}
