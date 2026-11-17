package ru.posts.security.rbac;

import java.util.Locale;

public enum RbacEffect {
    allow,
    deny;

    public static RbacEffect from(String value) {
        return RbacEffect.valueOf(value.trim().toLowerCase(Locale.ROOT));
    }
}
