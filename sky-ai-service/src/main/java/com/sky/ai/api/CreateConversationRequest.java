package com.sky.ai.api;

import jakarta.validation.constraints.Size;

public record CreateConversationRequest(
        @Size(max = 100) String title) {
}
