package com.sky.ai.agent.policy;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * CapabilityRegistry 维护允许开放给 Agent 的能力定义。
 */
@Component
public class CapabilityRegistry {

    private final Map<PolicyTypes.AiCapability, PolicyTypes.CapabilityDefinition> definitions;

    /**
     * 初始化 CapabilityRegistry，并注入其运行所需的依赖。
     */
    public CapabilityRegistry() {
        EnumMap<PolicyTypes.AiCapability, PolicyTypes.CapabilityDefinition> configured =
                new EnumMap<>(PolicyTypes.AiCapability.class);
        configured.put(PolicyTypes.AiCapability.GET_SHOP_STATUS, new PolicyTypes.CapabilityDefinition(
                "get_shop_status",
                "仅当用户询问门店当前是否营业、开门或打烊时调用。无需参数；"
                        + "返回 OPEN、CLOSED 或 UNKNOWN 状态。"));
        configured.put(PolicyTypes.AiCapability.GET_ORDER_PROGRESS, new PolicyTypes.CapabilityDefinition(
                "get_order_progress",
                "仅当用户询问已确认订单的进度、状态或配送情况时调用。无需参数；"
                        + "服务端会强制查询当前登录用户的该订单。"));
        configured.put(PolicyTypes.AiCapability.RECOMMEND_DISHES, new PolicyTypes.CapabilityDefinition(
                "recommend_dishes",
                "仅当用户希望推荐菜品、表达价格或口味偏好时调用。无需模型提供参数；"
                        + "应用会从会话结构化偏好中锁定预算、标签和过敏原，业务服务负责过滤排序。"));
        configured.put(PolicyTypes.AiCapability.RECOMMEND_MEAL_COMBO, new PolicyTypes.CapabilityDefinition(
                "recommend_meal_combo",
                "仅当用户已经确认整顿饭总预算和用餐人数时调用。无需模型提供参数；"
                        + "应用会锁定总预算、人数、口味和过敏原，业务服务负责组合并复核总价。"));
        definitions = Map.copyOf(configured);
    }

    /**
     * 获取指定能力定义，不存在时立即失败。
     */
    public PolicyTypes.CapabilityDefinition require(PolicyTypes.AiCapability capability) {
        PolicyTypes.CapabilityDefinition definition = definitions.get(capability);
        if (definition == null) {
            throw new IllegalArgumentException("unregistered AI capability: " + capability);
        }
        return definition;
    }

    /**
     * 校验能力集合是否全部登记在白名单中。
     */
    public void validate(Set<PolicyTypes.AiCapability> capabilities) {
        capabilities.forEach(this::require);
    }
}
