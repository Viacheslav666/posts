package ru.posts.common.s3;

import java.net.URI;
import java.net.URISyntaxException;

/** Rewrites presigned S3 URLs from internal SDK endpoints to a browser-reachable base. */
public final class S3PresignUrls {

    private S3PresignUrls() {}

    /**
     * When {@code publicBaseUrl} is blank, returns {@code presignedUrl} unchanged.
     *
     * <p>Example: presigned {@code http://minio:9090/posts-media/key?X-Amz-…} with public base
     * {@code http://100.102.9.56:3000/s3} → {@code http://100.102.9.56:3000/s3/posts-media/key?X-Amz-…}.
     */
    public static String toPublicUrl(String presignedUrl, String publicBaseUrl) {
        if (presignedUrl == null || presignedUrl.isBlank()) {
            return presignedUrl;
        }
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return presignedUrl;
        }
        try {
            URI src = URI.create(presignedUrl);
            String base = publicBaseUrl.endsWith("/")
                    ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                    : publicBaseUrl;
            String path = src.getRawPath() == null ? "" : src.getRawPath();
            if (!path.isEmpty() && !path.startsWith("/")) {
                path = "/" + path;
            }
            String query = src.getRawQuery();
            String rebuilt = base + path + (query == null || query.isEmpty() ? "" : "?" + query);
            return rebuilt;
        } catch (IllegalArgumentException ex) {
            return presignedUrl;
        }
    }

    public static boolean isDockerInternalHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String h = host.toLowerCase();
        return h.equals("minio")
                || h.equals("localhost")
                || h.equals("127.0.0.1")
                || h.endsWith(".internal")
                || !h.contains(".");
    }

    public static String hostOf(String url) {
        try {
            return new URI(url).getHost();
        } catch (URISyntaxException ex) {
            return null;
        }
    }
}
