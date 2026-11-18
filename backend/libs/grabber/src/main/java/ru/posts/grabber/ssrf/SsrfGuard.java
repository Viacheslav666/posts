package ru.posts.grabber.ssrf;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;

/**
 * Blocks private/loopback/link-local/metadata endpoints for RSS (and future website) fetches.
 * BE-17b will extend this for HTML sites; basic guard is required for RSS now.
 */
public final class SsrfGuard {

    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 3_000;
    public static final int DEFAULT_READ_TIMEOUT_MS = 10_000;
    public static final int DEFAULT_MAX_BYTES = 2 * 1024 * 1024;

    private SsrfGuard() {}

    public static void validateHttpUrl(String rawUrl) {
        validateHttpUrl(rawUrl, Set.of());
    }

    public static void validateHttpUrl(String rawUrl, Collection<String> allowedHosts) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new SsrfBlockedException("URL is blank");
        }
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException ex) {
            throw new SsrfBlockedException("Malformed URL", ex);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new SsrfBlockedException("Only http(s) URLs are allowed");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new SsrfBlockedException("URL host is required");
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (allowedHosts != null
                && allowedHosts.stream().anyMatch(h -> h != null && h.equalsIgnoreCase(normalizedHost))) {
            return;
        }
        if ("localhost".equals(normalizedHost)
                || normalizedHost.endsWith(".localhost")
                || normalizedHost.endsWith(".local")
                || "metadata.google.internal".equals(normalizedHost)) {
            throw new SsrfBlockedException("Host is not allowed: " + host);
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException ex) {
            throw new SsrfBlockedException("Cannot resolve host: " + host, ex);
        }
        if (addresses.length == 0) {
            throw new SsrfBlockedException("Cannot resolve host: " + host);
        }
        for (InetAddress address : addresses) {
            if (isBlockedAddress(address)) {
                throw new SsrfBlockedException("Resolved address is private/reserved: " + address.getHostAddress());
            }
        }
    }

    public static boolean isBlockedAddress(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isUniqueLocal(address)
                || isMetadataIpv4(address);
    }

    private static boolean isUniqueLocal(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }

    private static boolean isMetadataIpv4(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length != 4) {
            return false;
        }
        return (bytes[0] & 0xff) == 169 && (bytes[1] & 0xff) == 254;
    }
}
