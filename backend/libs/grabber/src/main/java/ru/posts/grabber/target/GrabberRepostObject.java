package ru.posts.grabber.target;

import java.util.Map;
import java.util.regex.Pattern;

/** Builds a VK {@code wall.repost} object id ({@code wall{ownerId}_{postId}}). */
public final class GrabberRepostObject {

    private static final Pattern NUMERIC = Pattern.compile("^-?\\d+$");
    private static final Pattern POST = Pattern.compile("^\\d+$");
    /** Form stored by {@code VkWallFetcher}: {@code wall-123_456}. */
    private static final Pattern WALL = Pattern.compile("^wall-?\\d+_\\d+$");

    private GrabberRepostObject() {}

    public static String from(String sourceId, String remotePostId, Map<String, Object> raw) {
        if (remotePostId == null) {
            return null;
        }
        String trimmed = remotePostId.trim();
        if (WALL.matcher(trimmed).matches()) {
            return trimmed;
        }
        if (!POST.matcher(trimmed).matches()) {
            return null;
        }
        String owner = null;
        if (raw != null && raw.get("owner_id") != null) {
            owner = String.valueOf(raw.get("owner_id")).trim();
        }
        if (owner == null
                || owner.isEmpty()
                || "null".equals(owner)
                || !NUMERIC.matcher(owner).matches()) {
            owner = sourceId == null ? null : sourceId.trim();
        }
        if (owner == null || !NUMERIC.matcher(owner).matches()) {
            return null;
        }
        return "wall" + owner + "_" + trimmed;
    }
}
