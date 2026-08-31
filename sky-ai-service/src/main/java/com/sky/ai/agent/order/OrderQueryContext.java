package com.sky.ai.agent.order;

/** 保存订单查询的短期待补充状态。 */
public record OrderQueryContext(OrderClarification pendingClarification,
                                long updatedSequence) {

    /** 将空状态规范化为 NONE。 */
    public OrderQueryContext {
        pendingClarification = pendingClarification == null
                ? OrderClarification.NONE : pendingClarification;
    }

    /** 创建没有待补参数的空上下文。 */
    public static OrderQueryContext empty() {
        return new OrderQueryContext(OrderClarification.NONE, 0L);
    }
}
