package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.vo.ai.OrderProgressVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiOrderProgressServiceImplTest {

    private final OrderMapper orderMapper = mock(OrderMapper.class);
    private final AiOrderProgressServiceImpl service = new AiOrderProgressServiceImpl(orderMapper);

    @Test
    void returnsOnlyOrderOwnedByAuthenticatedUser() {
        when(orderMapper.getProgressByIdAndUserId(201L, 1L)).thenReturn(null);

        OrderProgressVO result = service.getOrderProgress(201L, 1L);

        assertNull(result);
        verify(orderMapper).getProgressByIdAndUserId(201L, 1L);
    }

    @Test
    void calculatesAllowedActionsInBusinessCode() {
        LocalDateTime expected = LocalDateTime.of(2026, 8, 19, 12, 35);
        Orders order = Orders.builder()
                .id(102L)
                .status(Orders.TO_BE_CONFIRMED)
                .estimatedDeliveryTime(expected)
                .build();
        when(orderMapper.getProgressByIdAndUserId(102L, 1L)).thenReturn(order);

        OrderProgressVO result = service.getOrderProgress(102L, 1L);

        assertEquals("TO_BE_CONFIRMED", result.getStatus());
        assertEquals("等待商家接单", result.getStatusText());
        assertEquals(expected, result.getEstimatedDeliveryTime());
        assertTrue(result.getAllowedUserActions().contains("REQUEST_CANCEL"));
        assertFalse(result.isTrackingAvailable());
    }
}
