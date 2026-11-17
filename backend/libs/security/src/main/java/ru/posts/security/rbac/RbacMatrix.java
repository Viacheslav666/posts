package ru.posts.security.rbac;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Immutable RBAC matrix loaded from roles-permissions.yaml. */
public final class RbacMatrix {

    private final String version;
    private final Map<String, RbacMatrixEntry> byAction;

    public RbacMatrix(String version, Collection<RbacMatrixEntry> entries) {
        this.version = version == null ? "unknown" : version;
        Map<String, RbacMatrixEntry> map = new LinkedHashMap<>();
        for (RbacMatrixEntry entry : entries) {
            map.put(entry.action(), entry);
        }
        this.byAction = Map.copyOf(map);
    }

    public String version() {
        return version;
    }

    public Optional<RbacMatrixEntry> find(String action) {
        return Optional.ofNullable(byAction.get(action));
    }

    public RbacMatrixEntry require(String action) {
        return find(action).orElseThrow(() -> new IllegalArgumentException("Unknown RBAC action: " + action));
    }

    public Collection<RbacMatrixEntry> entries() {
        return byAction.values();
    }
}
