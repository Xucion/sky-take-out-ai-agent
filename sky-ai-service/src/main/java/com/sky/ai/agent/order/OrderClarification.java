package com.sky.ai.agent.order;

/** 标识订单查询会话当前正在等待用户补充的信息。 */
public enum OrderClarification {
    /** 当前没有待补充的订单查询参数。 */
    NONE,
    /** 等待用户提供订单 ID。 */
    ORDER_ID
}
