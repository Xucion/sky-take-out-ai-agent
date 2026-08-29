package com.sky.ai.policy;

import java.util.Set;

public record PolicyDecision(PolicyAction action,
                             Set<AiCapability> allowedCapabilities,
                             String responseCode,
                             String safeMessage) {

    public PolicyDecision {
        allowedCapabilities = allowedCapabilities == null
                ? Set.of() : Set.copyOf(allowedCapabilities);
    }
}
