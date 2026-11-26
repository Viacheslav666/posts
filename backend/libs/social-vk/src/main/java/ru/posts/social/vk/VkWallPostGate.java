package ru.posts.social.vk;

import java.util.Locale;

/**
 * VK ID user tokens ({@code vk2.a.*} from id.vk.ru) cannot call {@code wall.post} on a personal
 * profile (error 1051). Community tokens and classic oauth.vk.com tokens are allowed, so a later
 * reconnect with one of those just works.
 */
public final class VkWallPostGate {

    public static final String BLOCK_MESSAGE =
            "VK не разрешает публикацию с этим типом подключения. Переподключите канал.";

    public static final String VK_ID_TOKEN_PREFIX = "vk2.a.";

    /** VK: method is not available for this profile type. */
    public static final String PROFILE_TYPE_ERROR = "1051";

    private VkWallPostGate() {}

    public static boolean isVkIdToken(String accessToken) {
        return accessToken != null && accessToken.startsWith(VK_ID_TOKEN_PREFIX);
    }

    /**
     * Personal profile: explicit type {@code profile}, or a non-community owner id (not negative)
     * when the type is not a group/page.
     */
    public static boolean isPersonalProfile(String externalId, String channelType) {
        String type = normalizeType(channelType);
        if ("profile".equals(type)) {
            return true;
        }
        if ("group".equals(type) || "page".equals(type)) {
            return false;
        }
        return externalId != null && !externalId.isBlank() && !externalId.trim().startsWith("-");
    }

    /** {@code from_group=1} only for a community wall, never for a personal profile. */
    public static boolean postFromGroup(String externalId, String channelType) {
        if (isPersonalProfile(externalId, channelType)) {
            return false;
        }
        String type = normalizeType(channelType);
        if ("group".equals(type) || "page".equals(type)) {
            return true;
        }
        return externalId != null && externalId.trim().startsWith("-");
    }

    /** VK ID token on a personal profile cannot call wall.post. */
    public static boolean cannotPost(String accessToken, String externalId, String channelType) {
        return isVkIdToken(accessToken) && isPersonalProfile(externalId, channelType);
    }

    /** Channel already rejected with 1051 (status {@code no_permissions}). */
    public static boolean blockedByPriorDenial(String channelStatus) {
        return channelStatus != null && "no_permissions".equalsIgnoreCase(channelStatus.trim());
    }

    public static boolean blocked(String accessToken, String externalId, String channelType, String channelStatus) {
        return blockedByPriorDenial(channelStatus) || cannotPost(accessToken, externalId, channelType);
    }

    private static String normalizeType(String channelType) {
        return channelType == null ? "" : channelType.trim().toLowerCase(Locale.ROOT);
    }
}
