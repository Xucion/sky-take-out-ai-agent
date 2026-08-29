package com.sky.ai.api;

import java.util.List;

public record ConversationPageResponse(
        List<ConversationView> items,
        int limit,
        int offset,
        boolean hasMore) {
}
