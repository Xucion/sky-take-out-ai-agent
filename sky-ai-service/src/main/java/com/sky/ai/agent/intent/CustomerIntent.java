package com.sky.ai.agent.intent;

/**
 * 定义客服当前支持识别的用户意图。
 */
public enum CustomerIntent {
    SHOP_STATUS_QUERY("SHOP_STATUS"),
    ORDER_PROGRESS_QUERY("ORDER_PROGRESS"),
    DISH_RECOMMENDATION("DISH_RECOMMENDATION"),
    REFUND_REQUEST("REFUND_REQUEST"),
    UNKNOWN("GENERAL");

    private final String responseCode;

    /**
     * 创建用户意图并绑定对外返回的稳定响应码。
     */
    CustomerIntent(String responseCode) {
        this.responseCode = responseCode;
    }

    /**
     * 返回当前意图对应的稳定响应码。
     */
    public String responseCode() {
        return responseCode;
    }
}
