package ru.posts.ord.api;

/**
 * Hard guard for live ORD sandbox tests (QA-13 / BE-26).
 *
 * <p>VK ORD nightlies MUST only call {@code https://api-sandbox.ord.vk.com}. Hitting production
 * {@code https://api.ord.vk.com} would send test data into ЕРИР — that is never allowed in any
 * test or CI config. Yandex live tests are limited to prestable ({@code ord-prestable.yandex.net}).
 *
 * <p>WireMock / emulator base URLs (localhost, http://127.0.0.1, …) are out of scope for this
 * guard; callers apply it only when constructing a live sandbox client.
 */
public final class OrdSandboxGuard {

    public static final String VK_SANDBOX_HOST = "api-sandbox.ord.vk.com";
    public static final String VK_PRODUCTION_HOST = "api.ord.vk.com";
    public static final String YANDEX_PRESTABLE_HOST = "ord-prestable.yandex.net";
    public static final String YANDEX_PRODUCTION_HOST = "ord.yandex.net";

    private OrdSandboxGuard() {}

    /**
     * Fails fast unless {@code baseUrl} targets the VK ORD sandbox host and never production.
     *
     * @throws AssertionError when the URL is missing, production, or not sandbox
     */
    public static void assertVkOrdSandboxBaseUrl(String baseUrl) {
        String normalized = normalize(baseUrl);
        if (normalized.isEmpty()) {
            throw new AssertionError("VK ORD sandbox base URL must not be blank");
        }
        if (normalized.contains(VK_PRODUCTION_HOST) && !normalized.contains(VK_SANDBOX_HOST)) {
            throw new AssertionError(
                    "Refusing VK ORD base URL pointing at production api.ord.vk.com (ЕРИР risk): " + baseUrl);
        }
        if (!normalized.contains(VK_SANDBOX_HOST)) {
            throw new AssertionError("VK ORD live tests must use https://api-sandbox.ord.vk.com, got: " + baseUrl);
        }
    }

    /**
     * Fails fast unless {@code baseUrl} targets Yandex ORD prestable (never production).
     *
     * @throws AssertionError when the URL is missing, production, or not prestable
     */
    public static void assertYandexOrdPrestableBaseUrl(String baseUrl) {
        String normalized = normalize(baseUrl);
        if (normalized.isEmpty()) {
            throw new AssertionError("Yandex ORD prestable base URL must not be blank");
        }
        if (!normalized.contains(YANDEX_PRESTABLE_HOST)) {
            // Reject production (and anything else) when prestable host is absent.
            // Note: "ord-prestable.yandex.net".endsWith("ord.yandex.net") is true — never use endsWith.
            throw new AssertionError("Yandex ORD live tests must use https://ord-prestable.yandex.net"
                    + " (never production ord.yandex.net), got: "
                    + baseUrl);
        }
    }

    private static String normalize(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        return baseUrl.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
