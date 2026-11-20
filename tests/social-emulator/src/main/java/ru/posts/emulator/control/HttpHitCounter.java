package ru.posts.emulator.control;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Counts inbound HTTP API calls (excludes {@code /_control/**}) for compose-full traffic checks. */
@Component
public class HttpHitCounter extends OncePerRequestFilter {

    private final AtomicLong total = new AtomicLong();
    private final ConcurrentHashMap<String, AtomicLong> byPrefix = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path != null && !path.startsWith("/_control")) {
            total.incrementAndGet();
            String prefix = prefixOf(path);
            byPrefix.computeIfAbsent(prefix, k -> new AtomicLong()).incrementAndGet();
        }
        filterChain.doFilter(request, response);
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", total.get());
        Map<String, Long> prefixes = new LinkedHashMap<>();
        byPrefix.forEach((k, v) -> prefixes.put(k, v.get()));
        body.put("byPrefix", prefixes);
        return body;
    }

    public void reset() {
        total.set(0);
        byPrefix.clear();
    }

    private static String prefixOf(String path) {
        if (path.startsWith("/method/") || path.startsWith("/vk/method/")) {
            return "vk-method";
        }
        if (path.startsWith("/bot")) {
            return "telegram";
        }
        if (path.startsWith("/max") || path.startsWith("/messages")) {
            return "max";
        }
        if (path.startsWith("/fb.do") || path.startsWith("/ok/")) {
            return "ok";
        }
        if (path.startsWith("/oauth2") || path.equals("/authorize")) {
            return "vk-id-oauth";
        }
        if (path.startsWith("/yandex/")) {
            return "yandex-oauth";
        }
        if (path.startsWith("/yookassa/")) {
            return "yookassa";
        }
        if (path.startsWith("/ord/")) {
            return "ord-vk";
        }
        if (path.startsWith("/oauth/")) {
            return "channel-oauth";
        }
        return "other";
    }
}