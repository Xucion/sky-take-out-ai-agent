package com.sky.ai.agent.policy;

import com.sky.ai.agent.intent.CustomerIntent;
import com.sky.ai.agent.intent.IntentResolution;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * PolicyEngine 根据输入规则生成确定性的策略结果。
 */
@Component
public class PolicyEngine {

    /**
     * 根据意图解析结果生成能力授权决策。
     */
    public PolicyTypes.PolicyDecision decide(IntentResolution resolution) {
        if (resolution.intent() == CustomerIntent.REFUND_REQUEST) {
            return new PolicyTypes.PolicyDecision(PolicyTypes.PolicyAction.UNSUPPORTED, Set.of(),
                    "UNSUPPORTED_WRITE",
                    "当前智能客服暂不支持发起或处理退款，也不会因为提供订单 ID 而获得退款权限。"
                            + "当前我只能查询门店营业状态和订单进度。");
        }
        if (resolution.intent() == CustomerIntent.SHOP_STATUS_QUERY) {
            return allow(PolicyTypes.AiCapability.GET_SHOP_STATUS);
        }
        if (resolution.intent() == CustomerIntent.ORDER_PROGRESS_QUERY) {
            if (resolution.orderId() == null) {
                return new PolicyTypes.PolicyDecision(PolicyTypes.PolicyAction.REQUIRE_PARAMETER, Set.of(),
                        "ORDER_ID_REQUIRED",
                        "请提供需要查询的订单 ID，我只会查询当前登录账号下的订单。");
            }
            return allow(PolicyTypes.AiCapability.GET_ORDER_PROGRESS);
        }

        EnumSet<PolicyTypes.AiCapability> fallback = EnumSet.of(PolicyTypes.AiCapability.GET_SHOP_STATUS);
        if (resolution.orderId() != null) {
            fallback.add(PolicyTypes.AiCapability.GET_ORDER_PROGRESS);
        }
        return new PolicyTypes.PolicyDecision(PolicyTypes.PolicyAction.ALLOW_AGENT,
                fallback, null, null);
    }

    /**
     * 创建允许调用指定能力的策略结果。
     */
    private PolicyTypes.PolicyDecision allow(PolicyTypes.AiCapability capability) {
        return new PolicyTypes.PolicyDecision(PolicyTypes.PolicyAction.ALLOW_AGENT,
                Set.of(capability), null, null);
    }
}
