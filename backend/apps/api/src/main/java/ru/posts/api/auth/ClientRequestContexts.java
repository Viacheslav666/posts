package ru.posts.api.auth;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientRequestContexts {

    private ClientRequestContexts() {}

    public static ClientRequestContext from(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        return new ClientRequestContext(ip, request.getHeader("User-Agent"));
    }
}
