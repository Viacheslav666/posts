package ru.posts.persistence.jdbc;

import java.util.List;

/** Decides the aggregate post status from publication states. */
public final class PostPublicationRollup {

    private PostPublicationRollup() {}

    /**
     * {@code failed} only when the post has at least one publication and every one of them is
     * {@code failed_permanent}.
     */
    public static boolean allFailedPermanent(List<String> states) {
        if (states == null || states.isEmpty()) {
            return false;
        }
        for (String state : states) {
            if (!"failed_permanent".equals(state)) {
                return false;
            }
        }
        return true;
    }
}
