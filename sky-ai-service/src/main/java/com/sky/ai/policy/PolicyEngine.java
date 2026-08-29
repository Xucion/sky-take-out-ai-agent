package com.sky.ai.policy;

import com.sky.ai.intent.CustomerIntent;
import com.sky.ai.intent.IntentResolution;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

@Component
public class PolicyEngine {

    public PolicyDecision decide(IntentResolution resolution) {
        if (resolution.intent() == CustomerIntent.REFUND_REQUEST) {
            return new PolicyDecision(PolicyAction.UNSUPPORTED, Set.of(),
                    "UNSUPPORTED_WRITE",
                    "当前智能客服暂不支持发起或处理退款，也不会因为提供订单 ID 而获得退款权限。"
                            + "当前我只能查询门店营业状态和订单进度。");
        }
        if (resolution.intent() == CustomerIntent.SHOP_STATUS_QUERY) {
            return allow(AiCapability.GET_SHOP_STATUS);
        }
        if (resolution.intent() == CustomerIntent.ORDER_PROGRESS_QUERY) {
            if (resolution.orderId() == null) {
                return new PolicyDecision(PolicyAction.REQUIRE_PARAMETER, Set.of(),
                        "ORDER_ID_REQUIRED",
                        "请提供需要查询的订单 ID，我只会查询当前登录账号下的订单。");
            }
            return allow(AiCapability.GET_ORDER_PROGRESS);
        }

        EnumSet<AiCapability> fallback = EnumSet.of(AiCapability.GET_SHOP_STATUS);
        if (resolution.orderId() != null) {
            fallback.add(AiCapability.GET_ORDER_PROGRESS);
        }
        return new PolicyDecision(PolicyAction.ALLOW_AGENT,
                fallback, null, null);
    }

    private PolicyDecision allow(AiCapability capability) {
        return new PolicyDecision(PolicyAction.ALLOW_AGENT,
                Set.of(capability), null, null);
    }
}
