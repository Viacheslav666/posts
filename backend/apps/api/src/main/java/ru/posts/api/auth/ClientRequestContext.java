package ru.posts.api.auth;

public record ClientRequestContext(String ip, String userAgent) {

    public static ClientRequestContext anonymous() {
        return new ClientRequestContext(null, null);
    }
}
