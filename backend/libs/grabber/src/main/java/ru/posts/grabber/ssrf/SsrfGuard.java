package ru.posts.grabber.ssrf;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;

/**
 * Blocks private/loopback/link-local/metadata endpoints for all grabber external fetches. Validates
 * scheme, host, and resolved addresses (IPv4/IPv6). Re-check on every redirect to mitigate DNS
 * rebinding.
 */
public final class SsrfGuard {

    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 3_000;
    public static final int DEFAULT_READ_TIMEOUT_MS = 10_000;
    public static final int DEFAULT_MAX_BYTES = 2 * 1024 * 1024;
    public static final int DEFAULT_MAX_REDIRECTS = 5;

    private SsrfGuard() {}

    public static void validateHttpUrl(String rawUrl) {
        validateHttpUrl(rawUrl, Set.of(), HostResolver.SYSTEM);
    }

    public static void validateHttpUrl(String rawUrl, Collection<String> allowedHosts) {
        validateHttpUrl(rawUrl, allowedHosts, HostResolver.SYSTEM);
    }

    public static URI validateHttpUrl(String rawUrl, Collection<String> allowedHosts, HostResolver resolver) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new SsrfBlockedException("URL is blank");
        }
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException ex) {
            throw new SsrfBlockedException("Malformed URL", ex);
        }
        return validateUri(uri, allowedHosts, resolver);
    }

    public static URI validateUri(URI uri, Collection<String> allowedHosts, HostResolver resolver) {
        if (uri == null) {
            throw new SsrfBlockedException("URL is blank");
        }
        if (uri.isOpaque()) {
            throw new SsrfBlockedException("Opaque URLs are not allowed");
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
        if (isAllowedHost(normalizedHost, allowedHosts)) {
            return uri;
        }
        if ("localhost".equals(normalizedHost)
                || normalizedHost.endsWith(".localhost")
                || normalizedHost.endsWith(".local")
                || "metadata.google.internal".equals(normalizedHost)
                || normalizedHost.endsWith(".internal")
                || "metadata".equals(normalizedHost)) {
            throw new SsrfBlockedException("Host is not allowed: " + host);
        }
        // Literal IP in host — check without DNS.
        InetAddress literal = tryParseLiteral(normalizedHost);
        if (literal != null) {
            if (isBlockedAddress(literal)) {
                throw new SsrfBlockedException("Resolved address is private/reserved: " + literal.getHostAddress());
            }
            return uri;
        }
        InetAddress[] addresses;
        try {
            addresses = resolver == null ? HostResolver.SYSTEM.resolve(host) : resolver.resolve(host);
        } catch (UnknownHostException ex) {
            throw new SsrfBlockedException("Cannot resolve host: " + host, ex);
        }
        if (addresses == null || addresses.length == 0) {
            throw new SsrfBlockedException("Cannot resolve host: " + host);
        }
        for (InetAddress address : addresses) {
            if (isBlockedAddress(address)) {
                throw new SsrfBlockedException("Resolved address is private/reserved: " + address.getHostAddress());
            }
        }
        return uri;
    }

    public static boolean isAllowedHost(String host, Collection<String> allowedHosts) {
        if (host == null || allowedHosts == null || allowedHosts.isEmpty()) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        return allowedHosts.stream().anyMatch(h -> h != null && h.equalsIgnoreCase(normalized));
    }

    public static boolean isBlockedAddress(InetAddress address) {
        if (address == null) {
            return true;
        }
        InetAddress unwrapped = unwrapMapped(address);
        return unwrapped.isAnyLocalAddress()
                || unwrapped.isLoopbackAddress()
                || unwrapped.isLinkLocalAddress()
                || unwrapped.isSiteLocalAddress()
                || unwrapped.isMulticastAddress()
                || isUniqueLocal(unwrapped)
                || isCarrierGradeNat(unwrapped)
                || isDocumentationOrReserved(unwrapped)
                || isMetadataIpv4(unwrapped);
    }

    private static InetAddress unwrapMapped(InetAddress address) {
        if (address instanceof Inet6Address inet6 && inet6.isIPv4CompatibleAddress()) {
            byte[] bytes = inet6.getAddress();
            try {
                return InetAddress.getByAddress(new byte[] {bytes[12], bytes[13], bytes[14], bytes[15]});
            } catch (UnknownHostException ignored) {
                return address;
            }
        }
        // IPv4-mapped IPv6 (::ffff:x.x.x.x)
        if (address instanceof Inet6Address) {
            byte[] bytes = address.getAddress();
            boolean mapped = true;
            for (int i = 0; i < 10; i++) {
                if (bytes[i] != 0) {
                    mapped = false;
                    break;
                }
            }
            if (mapped && (bytes[10] & 0xff) == 0xff && (bytes[11] & 0xff) == 0xff) {
                try {
                    return InetAddress.getByAddress(new byte[] {bytes[12], bytes[13], bytes[14], bytes[15]});
                } catch (UnknownHostException ignored) {
                    return address;
                }
            }
        }
        return address;
    }

    private static boolean isUniqueLocal(InetAddress address) {
        byte[] bytes = address.getAddress();
        // fc00::/7
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }

    private static boolean isCarrierGradeNat(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length != 4) {
            return false;
        }
        int b0 = bytes[0] & 0xff;
        int b1 = bytes[1] & 0xff;
        // 100.64.0.0/10
        return b0 == 100 && b1 >= 64 && b1 <= 127;
    }

    private static boolean isDocumentationOrReserved(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length != 4) {
            return false;
        }
        int b0 = bytes[0] & 0xff;
        int b1 = bytes[1] & 0xff;
        // 0.0.0.0/8
        if (b0 == 0) {
            return true;
        }
        // 192.0.0.0/24 (IETF), 192.0.2.0/24 (TEST-NET-1), 198.51.100.0/24, 203.0.113.0/24
        if (b0 == 192 && b1 == 0) {
            return true;
        }
        if (b0 == 198 && b1 == 51) {
            return true;
        }
        if (b0 == 203 && b1 == 0) {
            return true;
        }
        // 198.18.0.0/15 benchmarking
        if (b0 == 198 && (b1 == 18 || b1 == 19)) {
            return true;
        }
        // 240.0.0.0/4 reserved
        return b0 >= 240;
    }

    private static boolean isMetadataIpv4(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length != 4) {
            return false;
        }
        // 169.254.0.0/16 link-local / cloud metadata
        return (bytes[0] & 0xff) == 169 && (bytes[1] & 0xff) == 254;
    }

    private static boolean isDottedIpv4(String host) {
        int dots = 0;
        int octet = -1;
        for (int i = 0; i < host.length(); i++) {
            char ch = host.charAt(i);
            if (ch == '.') {
                if (octet < 0 || octet > 255) {
                    return false;
                }
                dots++;
                octet = -1;
                continue;
            }
            if (ch < '0' || ch > '9') {
                return false;
            }
            octet = octet < 0 ? (ch - '0') : octet * 10 + (ch - '0');
            if (octet > 255) {
                return false;
            }
        }
        return dots == 3 && octet >= 0 && octet <= 255;
    }

    private static InetAddress tryParseLiteral(String host) {
        // Strip IPv6 brackets if present in raw form (URI.getHost already strips them).
        String h = host;
        if (h.startsWith("[") && h.endsWith("]")) {
            h = h.substring(1, h.length() - 1);
        }
        try {
            if (h.indexOf(':') >= 0) {
                // IPv6 literal
                return InetAddress.getByName(h);
            }
            // Dotted IPv4 only when all octets numeric
            if (isDottedIpv4(h)) {
                return InetAddress.getByName(h);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }
}
