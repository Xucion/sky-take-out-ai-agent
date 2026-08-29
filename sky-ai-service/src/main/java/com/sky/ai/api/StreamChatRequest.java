package com.sky.ai.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record StreamChatRequest(
        @NotBlank @Size(max = 2000) String message,
        @Positive Long orderId,
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "[A-Za-z0-9._:-]+") String clientRequestId) {
}
