package com.sky.service.impl;

import com.sky.vo.ai.ShopStatusVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiShopStatusServiceImplTest {

    private ValueOperations valueOperations;
    private AiShopStatusServiceImpl service;

    @BeforeEach
    void setUp() {
        RedisTemplate redisTemplate = mock(RedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new AiShopStatusServiceImpl(redisTemplate);
    }

    @Test
    void mapsOneToOpen() {
        when(valueOperations.get("SHOP_STATUS")).thenReturn(1);
        ShopStatusVO result = service.getShopStatus();
        assertEquals("OPEN", result.getStatus());
        assertEquals("营业中", result.getStatusText());
        assertNotNull(result.getCheckedAt());
    }

    @Test
    void mapsZeroToClosed() {
        when(valueOperations.get("SHOP_STATUS")).thenReturn(0);
        assertEquals("CLOSED", service.getShopStatus().getStatus());
    }

    @Test
    void missingOrUnexpectedValueIsUnknown() {
        when(valueOperations.get("SHOP_STATUS")).thenReturn("1");
        assertEquals("UNKNOWN", service.getShopStatus().getStatus());

        when(valueOperations.get("SHOP_STATUS")).thenReturn(null);
        assertEquals("UNKNOWN", service.getShopStatus().getStatus());
    }

    @Test
    void redisFailureIsUnknown() {
        when(valueOperations.get("SHOP_STATUS")).thenThrow(new RuntimeException("redis unavailable"));
        assertEquals("UNKNOWN", service.getShopStatus().getStatus());
    }
}
