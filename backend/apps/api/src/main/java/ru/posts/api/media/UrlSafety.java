package ru.posts.api.media;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/** SSRF guards for server-side URL import. */
public final class UrlSafety {

    private UrlSafety() {}

    public static void validateHttpUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("URL is required");
        }
        URI uri;
        try {
            uri = URI.create(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("Only http(s) URLs are allowed");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("URL host is required");
        }
        assertHostSafe(uri.getHost());
    }

    public static void assertHostSafe(String host) {
        String h = host.trim().toLowerCase(Locale.ROOT);
        if ("localhost".equals(h) || h.endsWith(".localhost") || h.endsWith(".local")) {
            throw new IllegalArgumentException("Private or local hosts are not allowed");
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(h);
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("Unable to resolve host");
        }
        for (InetAddress address : addresses) {
            if (isBlockedAddress(address)) {
                throw new IllegalArgumentException("Private or link-local addresses are not allowed");
            }
        }
    }

    public static boolean isBlockedAddress(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isMetadataAddress(address);
    }

    private static boolean isMetadataAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        // 169.254.169.254 and 169.254.0.0/16 already covered by link-local; keep explicit cloud metadata.
        if (bytes.length == 4) {
            int b0 = bytes[0] & 0xff;
            int b1 = bytes[1] & 0xff;
            if (b0 == 169 && b1 == 254) {
                return true;
            }
            // 100.64.0.0/10 CGNAT often used for cloud metadata paths
            if (b0 == 100 && b1 >= 64 && b1 <= 127) {
                return true;
            }
        }
        return false;
    }
}
