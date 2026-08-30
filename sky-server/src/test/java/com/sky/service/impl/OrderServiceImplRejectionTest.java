package com.sky.service.impl;

import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceImplRejectionTest {

    @Test
    void rejectsSimulatedPaidOrderAndMarksItRefunded() {
        OrderMapper orderMapper = mock(OrderMapper.class);
        OrderServiceImpl service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);

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
    }

    @Test
    void userCancellationMarksSimulatedPaidOrderRefunded() {
        OrderMapper orderMapper = mock(OrderMapper.class);
        OrderServiceImpl service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);

        Orders existingOrder = Orders.builder()
                .id(101L)
                .number("202608200002")
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .build();
        when(orderMapper.getById(101L)).thenReturn(existingOrder);

        service.userCancelById(101L);

        ArgumentCaptor<Orders> updateCaptor = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).update(updateCaptor.capture());
        Orders update = updateCaptor.getValue();
        assertEquals(Orders.CANCELLED, update.getStatus());
        assertEquals(Orders.REFUND, update.getPayStatus());
        assertEquals("用户取消", update.getCancelReason());
        assertNotNull(update.getCancelTime());
    }

    @Test
    void adminCancellationMarksSimulatedPaidOrderRefunded() {
        OrderMapper orderMapper = mock(OrderMapper.class);
        OrderServiceImpl service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);

        Orders existingOrder = Orders.builder()
                .id(102L)
                .number("202608200003")
                .status(Orders.CONFIRMED)
                .payStatus(Orders.PAID)
                .build();
        when(orderMapper.getById(102L)).thenReturn(existingOrder);

        OrdersCancelDTO cancel = new OrdersCancelDTO();
        cancel.setId(102L);
        cancel.setCancelReason("临时闭店");
        service.cancel(cancel);

        ArgumentCaptor<Orders> updateCaptor = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).update(updateCaptor.capture());
        Orders update = updateCaptor.getValue();
        assertEquals(Orders.CANCELLED, update.getStatus());
        assertEquals(Orders.REFUND, update.getPayStatus());
        assertEquals("临时闭店", update.getCancelReason());
        assertNotNull(update.getCancelTime());
    }
}
