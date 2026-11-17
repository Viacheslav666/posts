package ru.posts.security.rbac;

import java.util.Locale;

/** Workspace membership roles from docs/product/roles-permissions.yaml. */
public enum RbacRole {
    owner,
    admin,
    editor,
    author,
    viewer;

    public static RbacRole from(String value) {
        return RbacRole.valueOf(value.trim().toLowerCase(Locale.ROOT));
    }

    /** Owner and Admin see all projects without explicit grants. */
    public boolean seesAllProjects() {
        return this == owner || this == admin;
    }
}
