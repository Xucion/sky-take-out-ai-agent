package com.sky.ai.tool;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 只接收 sky-server 主动公开的最小订单视图，不包含地址、电话和备注。
 */
public record OrderProgress(Long orderId,
                            String status,
                            String statusText,
                            @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
                            LocalDateTime estimatedDeliveryTime,
                            @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
                            LocalDateTime deliveredAt,
                            List<String> allowedUserActions,
                            boolean trackingAvailable,
                            String detailPath) {
}
