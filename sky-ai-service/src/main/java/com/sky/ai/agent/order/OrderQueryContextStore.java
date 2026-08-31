package com.sky.ai.agent.order;

/** 定义订单查询短期上下文的存取边界。 */
public interface OrderQueryContextStore {
    /** 读取指定会话的订单查询上下文。 */
    OrderQueryContext load(String conversationId);
    /** 保存指定会话的订单查询上下文。 */
    void save(String conversationId, OrderQueryContext context);
    /** 清除指定会话已经消费的订单查询上下文。 */
    void clear(String conversationId);
}
