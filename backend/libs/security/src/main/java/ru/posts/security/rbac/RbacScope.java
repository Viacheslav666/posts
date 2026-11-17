package ru.posts.security.rbac;

import java.util.Locale;

public enum RbacScope {
    workspace,
    assigned_projects,
    own_posts,
    billing;

    public static RbacScope from(String value) {
        if (value == null || value.isBlank()) {
            return workspace;
        }
        return RbacScope.valueOf(value.trim().toLowerCase(Locale.ROOT));
    }
}
