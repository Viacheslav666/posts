package ru.posts.security.rbac;

import java.util.Objects;

/** Stable action id (`resource.verb`) from the RBAC matrix. */
public final class RbacAction {

    private final String id;

    private RbacAction(String id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public static RbacAction of(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("RBAC action id is required");
        }
        return new RbacAction(id.trim());
    }

    public String id() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RbacAction that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id;
    }
}
