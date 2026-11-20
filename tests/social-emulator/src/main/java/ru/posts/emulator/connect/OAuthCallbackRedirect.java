package ru.posts.emulator.connect;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.util.UriComponentsBuilder;

/** Builds OAuth authorize redirects for allowlisted Posts channel callback URLs only. */
final class OAuthCallbackRedirect {

    private static final Pattern CALLBACK_PATH =
            Pattern.compile("^/app/([a-zA-Z0-9\\-]+)/channels/connect/(vk|ok)/callback/?$");
    private static final Pattern HOST = Pattern.compile("^[a-zA-Z0-9.\\-\\:]+$");

    private OAuthCallbackRedirect() {}

    static Optional<URI> toCallback(String redirectUri, String code, String state) {
        if (!ConnectApiController.isAllowedChannelRedirect(redirectUri)) {
            return Optional.empty();
        }
        final URI src;
        try {
            src = URI.create(redirectUri.trim());
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        String scheme = src.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            return Optional.empty();
        }
        String host = src.getHost();
        if (host == null || !HOST.matcher(host).matches()) {
            return Optional.empty();
        }
        String rawPath = src.getPath() == null ? "" : src.getPath();
        Matcher pathMatcher = CALLBACK_PATH.matcher(rawPath);
        if (!pathMatcher.matches()) {
            return Optional.empty();
        }
        String workspaceId = pathMatcher.group(1);
        String network = pathMatcher.group(2);
        URI target = UriComponentsBuilder.newInstance()
                .scheme(scheme.toLowerCase(Locale.ROOT))
                .host(host)
                .port(src.getPort())
                .path("/app/" + workspaceId + "/channels/connect/" + network + "/callback")
                .queryParam("code", code)
                .queryParam("state", state == null ? "" : state)
                .encode()
                .build()
                .toUri();
        return Optional.of(target);
    }
}
