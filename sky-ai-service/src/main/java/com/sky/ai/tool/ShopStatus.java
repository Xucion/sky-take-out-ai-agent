package com.sky.ai.tool;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record ShopStatus(String status,
                         String statusText,
                         @JsonFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime checkedAt) {
}
