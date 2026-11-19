package ru.posts.antispam.model;

import java.util.Map;

public record AntispamAction(AntispamActionKind kind, Map<String, Object> params) {

    public static AntispamAction flag() {
        return new AntispamAction(AntispamActionKind.FLAG, Map.of());
    }

    public static AntispamAction reply(String template) {
        return new AntispamAction(AntispamActionKind.REPLY, Map.of("template", template == null ? "" : template));
    }

    public static AntispamAction delete() {
        return new AntispamAction(AntispamActionKind.DELETE, Map.of());
    }

    public static AntispamAction ban(int days, String reason, boolean commentVisible) {
        return new AntispamAction(
                AntispamActionKind.BAN,
                Map.of(
                        "days", days,
                        "reason", reason == null ? "spam" : reason,
                        "comment_visible", commentVisible));
    }
}
