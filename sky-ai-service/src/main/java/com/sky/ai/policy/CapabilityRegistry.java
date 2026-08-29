package com.sky.ai.policy;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

@Component
public class CapabilityRegistry {

    private final Map<AiCapability, CapabilityDefinition> definitions;

    public CapabilityRegistry() {
        EnumMap<AiCapability, CapabilityDefinition> configured =
                new EnumMap<>(AiCapability.class);
        configured.put(AiCapability.GET_SHOP_STATUS, new CapabilityDefinition(
                "get_shop_status",
                "仅当用户询问门店当前是否营业、开门或打烊时调用。无需参数；"
                        + "返回 OPEN、CLOSED 或 UNKNOWN 状态。"));
        configured.put(AiCapability.GET_ORDER_PROGRESS, new CapabilityDefinition(
                "get_order_progress",
                "仅当用户询问已确认订单的进度、状态或配送情况时调用。无需参数；"
                        + "服务端会强制查询当前登录用户的该订单。"));
        definitions = Map.copyOf(configured);
    }

    public CapabilityDefinition require(AiCapability capability) {
        CapabilityDefinition definition = definitions.get(capability);
        if (definition == null) {
            throw new IllegalArgumentException("unregistered AI capability: " + capability);
        }
        return definition;
    }

    public void validate(Set<AiCapability> capabilities) {
        capabilities.forEach(this::require);
    }
}
