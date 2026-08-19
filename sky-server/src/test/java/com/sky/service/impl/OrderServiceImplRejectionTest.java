package com.sky.service.impl;

import com.sky.dto.OrdersRejectionDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.utils.WeChatPayUtil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderServiceImplRejectionTest {

    @Test
    void rejectsSimulatedPaidOrderWithoutCallingWechatRefund() throws Exception {
        OrderMapper orderMapper = mock(OrderMapper.class);
        WeChatPayUtil weChatPayUtil = mock(WeChatPayUtil.class);
        OrderServiceImpl service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "weChatPayUtil", weChatPayUtil);

        Orders existingOrder = Orders.builder()
                .id(100L)
                .number("202608200001")
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .build();
        when(orderMapper.getById(100L)).thenReturn(existingOrder);

        OrdersRejectionDTO rejection = new OrdersRejectionDTO();
        rejection.setId(100L);
        rejection.setRejectionReason("菜品已售完");

        service.rejection(rejection);

        ArgumentCaptor<Orders> updateCaptor = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).update(updateCaptor.capture());
        Orders update = updateCaptor.getValue();
        assertEquals(Orders.CANCELLED, update.getStatus());
        assertEquals(Orders.REFUND, update.getPayStatus());
        assertEquals("菜品已售完", update.getRejectionReason());
        assertNotNull(update.getCancelTime());
        verifyNoInteractions(weChatPayUtil);
    }
}
