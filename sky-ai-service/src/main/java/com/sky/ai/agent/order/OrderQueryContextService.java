package com.sky.ai.agent.order;

import com.sky.ai.common.exception.AiServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.OptionalLong;
import java.util.regex.Pattern;

/** 管理订单 ID 待补充状态及其安全解析。 */
@Service
public class OrderQueryContextService {

    private static final Pattern STANDALONE_INTEGER = Pattern.compile("\\d{1,19}");
    private final OrderQueryContextStore contextStore;

    /** 创建订单查询上下文服务。 */
    public OrderQueryContextService(OrderQueryContextStore contextStore) {
        this.contextStore = contextStore;
    }

    /** 记录当前会话正在等待订单 ID。 */
    public void markAwaitingOrderId(String conversationId, long sequence) {
        contextStore.save(conversationId,
                new OrderQueryContext(OrderClarification.ORDER_ID, sequence));
    }

    /** 仅在显式 ORDER_ID 状态下解析本轮纯数字回答。 */
    public OptionalLong resolvePendingOrderId(String conversationId, String message) {
        OrderQueryContext context = contextStore.load(conversationId);
        if (context.pendingClarification() != OrderClarification.ORDER_ID) {
            return OptionalLong.empty();
        }
        String normalized = message == null ? "" : message.trim();
        if (!STANDALONE_INTEGER.matcher(normalized).matches()) {
            return OptionalLong.empty();
        }
        try {
            long orderId = Long.parseLong(normalized);
            if (orderId <= 0) {
                throw invalidOrderId("订单 ID 必须是正整数");
            }
            return OptionalLong.of(orderId);
        } catch (NumberFormatException ex) {
            throw invalidOrderId("订单 ID 超出有效范围");
        }
    }

    /** 清除已经成功消费的订单 ID 待补状态。 */
    public void clear(String conversationId) {
        contextStore.clear(conversationId);
    }

    /** 创建稳定、安全的订单编号参数异常。 */
    private AiServiceException invalidOrderId(String message) {
        return new AiServiceException(HttpStatus.BAD_REQUEST, "INVALID_ORDER_ID", message);
    }
}
