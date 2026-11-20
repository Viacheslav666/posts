package ru.posts.emulator.connect;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds OAuth authorize redirects for allowlisted Posts channel callback URLs only. */
final class OAuthCallbackRedirect {

    private static final Pattern CALLBACK_PATH =
            Pattern.compile("^/app/([a-zA-Z0-9\\-]+)/channels/connect/(vk|ok)/callback/?$");

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
        String matchedBase = matchAllowedBase(allowedAppBaseUrls, src);
        if (matchedBase == null) {
            return Optional.empty();
        }
        String workspaceId = pathMatcher.group(1);
        String network = pathMatcher.group(2);
        String query = "code=" + enc(code) + "&state=" + enc(state == null ? "" : state);
        return Optional.of(matchedBase + "/app/" + workspaceId + "/channels/connect/" + network + "/callback?" + query);
    }

    private static String matchAllowedBase(List<String> allowedBases, URI redirect) {
        String redirectPrefix = basePrefix(redirect);
        for (String base : allowedBases) {
            if (redirectPrefix.equals(basePrefix(URI.create(base)))) {
                return base;
            }
        }
        return null;
    }

    private static String basePrefix(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        if (port < 0) {
            port = "https".equals(scheme) ? 443 : 80;
        }
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return scheme + "://" + host + (defaultPort ? "" : ":" + port);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
