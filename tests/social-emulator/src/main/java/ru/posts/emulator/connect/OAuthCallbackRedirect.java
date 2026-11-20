package ru.posts.emulator.connect;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds OAuth authorize redirects for allowlisted Posts channel callback URLs only. */
final class OAuthCallbackRedirect {

    private static final Pattern CALLBACK_PATH =
            Pattern.compile("^/app/([a-zA-Z0-9\\-]+)/channels/connect/(vk|ok)/callback/?$");
    private static final Pattern SAFE_TOKEN = Pattern.compile("^[A-Za-z0-9._\\-]+$");

    private OAuthCallbackRedirect() {}

    static Optional<String> locationHeader(
            List<String> allowedAppBaseUrls, String redirectUri, String code, String state) {
        if (!ConnectApiController.isAllowedChannelRedirect(redirectUri)) {
            return Optional.empty();
        }
        final URI src;
        try {
            src = URI.create(redirectUri.trim());
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        String path = src.getPath() == null ? "" : src.getPath();
        Matcher pathMatcher = CALLBACK_PATH.matcher(path);
        if (!pathMatcher.matches()) {
            return Optional.empty();
        }
        String matchedBase = matchConfiguredBase(allowedAppBaseUrls, redirectUri.trim());
        if (matchedBase == null) {
            return Optional.empty();
        }
        String workspaceId = pathMatcher.group(1);
        String network = pathMatcher.group(2);
        String safeCode = sanitizeToken(code, 128);
        String safeState = sanitizeToken(state, 256);
        if (safeCode.isEmpty()) {
            return Optional.empty();
        }
        String query = "code=" + enc(safeCode) + "&state=" + enc(safeState);
        return Optional.of(matchedBase + "/app/" + workspaceId + "/channels/connect/" + network + "/callback?" + query);
    }

    private static String matchConfiguredBase(List<String> allowedBases, String redirectUri) {
        for (String base : allowedBases) {
            if (redirectUri.regionMatches(true, 0, base, 0, base.length())
                    && redirectUri.regionMatches(false, base.length(), "/app/", 0, 5)) {
                return base;
            }
        }
        return null;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String sanitizeToken(String value, int maxLen) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLen || !SAFE_TOKEN.matcher(trimmed).matches()) {
            return "";
        }
        return trimmed;
    }
}
