package ru.posts.security.rbac;

import java.util.List;
import java.util.Map;

public record RbacMatrixEntry(
        String action, Map<RbacRole, RbacEffect> effects, RbacScope scope, Map<RbacRole, List<String>> conditions) {

    public RbacEffect effectFor(RbacRole role) {
        return effects.getOrDefault(role, RbacEffect.deny);
    }

    public List<String> conditionsFor(RbacRole role) {
        return conditions.getOrDefault(role, List.of());
    }
}
