package com.sky.ai.agent.policy;

import java.util.Set;

/**
 * Agent 能力授权与策略决策所使用的领域类型集合。
 */
public final class PolicyTypes {

    /**
     * 禁止实例化仅用于归类类型的工具类。
     */
    private PolicyTypes() {
    }

    /**
     * Agent 当前被允许调用的业务能力。
     */
    public enum AiCapability {
        GET_SHOP_STATUS,
        GET_ORDER_PROGRESS,
        RECOMMEND_DISHES,
        RECOMMEND_MEAL_COMBO
    }

    /**
     * 能力在模型侧对应的工具名称和说明。
     */
    public record CapabilityDefinition(String toolName, String description) {
    }

    /**
     * 策略引擎对当前请求给出的处理动作。
     */
    public enum PolicyAction {
        ALLOW_AGENT,
        REQUIRE_PARAMETER,
        UNSUPPORTED
    }

    /**
     * 策略引擎返回的能力集合及安全响应信息。
     */
    public record PolicyDecision(
            PolicyAction action,
            Set<AiCapability> allowedCapabilities,
            String responseCode,
            String safeMessage) {

        /**
         * 将允许能力规范化为不可变集合，避免空值和外部修改。
         */
        public PolicyDecision {
            allowedCapabilities = allowedCapabilities == null
                    ? Set.of() : Set.copyOf(allowedCapabilities);
        }
    }
}
