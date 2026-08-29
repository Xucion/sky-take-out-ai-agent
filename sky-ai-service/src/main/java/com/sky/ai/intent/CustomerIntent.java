package com.sky.ai.intent;

public enum CustomerIntent {
    SHOP_STATUS_QUERY("SHOP_STATUS"),
    ORDER_PROGRESS_QUERY("ORDER_PROGRESS"),
    REFUND_REQUEST("REFUND_REQUEST"),
    UNKNOWN("GENERAL");

    private final String responseCode;

    CustomerIntent(String responseCode) {
        this.responseCode = responseCode;
    }

    public String responseCode() {
        return responseCode;
    }
}
