package ru.posts.api.post;

import ru.posts.api.web.dto.post.PostDtos;

/** Normalizes variant options before persistence. */
public final class PostVariantOptionsSanitizer {

    private PostVariantOptionsSanitizer() {}

    /** VK/TG/Max: do not persist {@code unpinAt} without {@code pin=true}. */
    public static PostDtos.PostVariantOptions stripUnpinWithoutPin(PostDtos.PostVariantOptions options) {
        if (options == null || Boolean.TRUE.equals(options.pin()) || options.unpinAt() == null) {
            return options;
        }
        return new PostDtos.PostVariantOptions(
                options.pin(),
                null,
                options.deleteAfter(),
                options.mute(),
                options.closeComments(),
                options.signed(),
                options.firstComment(),
                options.buttons(),
                options.carouselMode(),
                options.donutDays(),
                options.coOwnerIds(),
                options.primaryAttachmentsMode(),
                options.poll(),
                options.geo(),
                options.copyrightUrl(),
                options.actionButton(),
                options.extra());
    }
}
