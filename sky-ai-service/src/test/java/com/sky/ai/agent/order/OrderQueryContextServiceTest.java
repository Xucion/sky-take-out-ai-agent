package com.sky.ai.agent.order;

import com.sky.ai.common.exception.AiServiceException;
import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证订单 ID 显式待补状态的保存、解析和消费。 */
class OrderQueryContextServiceTest {

    /** 验证第一轮缺少订单号时保存 ORDER_ID 状态。 */
    @Test
    void savesAwaitingOrderIdState() {
        OrderQueryContextStore store = mock(OrderQueryContextStore.class);
        OrderQueryContextService service = new OrderQueryContextService(store);

        service.markAwaitingOrderId("c1", 3L);

        verify(store).save("c1", new OrderQueryContext(OrderClarification.ORDER_ID, 3L));
    }

    /** 验证仅在 ORDER_ID 状态下接受纯数字回答。 */
    @Test
    void resolvesStandaloneNumberOnlyWhenOrderIdIsPending() {
        OrderQueryContextStore store = mock(OrderQueryContextStore.class);
        when(store.load("c2")).thenReturn(
                new OrderQueryContext(OrderClarification.ORDER_ID, 1L));
        OrderQueryContextService service = new OrderQueryContextService(store);

        OptionalLong resolved = service.resolvePendingOrderId("c2", "19");

        assertEquals(19L, resolved.orElseThrow());
    }

    /** 验证没有 ORDER_ID 状态时孤立数字不会触发订单查询。 */
    @Test
    void rejectsStandaloneNumberWithoutPendingState() {
        OrderQueryContextStore store = mock(OrderQueryContextStore.class);
        when(store.load("c3")).thenReturn(OrderQueryContext.empty());
        OrderQueryContextService service = new OrderQueryContextService(store);

        assertFalse(service.resolvePendingOrderId("c3", "19").isPresent());
    }

    /** 验证超出 long 范围的待补订单 ID 被明确拒绝。 */
    @Test
    void rejectsOverflowingPendingOrderId() {
        OrderQueryContextStore store = mock(OrderQueryContextStore.class);
        when(store.load("c4")).thenReturn(
                new OrderQueryContext(OrderClarification.ORDER_ID, 1L));
        OrderQueryContextService service = new OrderQueryContextService(store);

        AiServiceException exception = assertThrows(AiServiceException.class,
                () -> service.resolvePendingOrderId("c4", "9999999999999999999"));

        assertEquals("INVALID_ORDER_ID", exception.getCode());
    }
}
