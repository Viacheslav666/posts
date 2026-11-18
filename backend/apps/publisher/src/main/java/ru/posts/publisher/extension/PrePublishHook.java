package ru.posts.publisher.extension;

import ru.posts.persistence.jdbc.PublicationClaim;
import ru.posts.social.api.PostVariantContent;

/**
 * Extension point for BE-15 (ORD/erid marker insertion) and similar pre-network transforms.
 *
 * <p>Implementations may rewrite content (insert «Реклама… erid:…») or throw a permanent failure
 * when ads are enabled but erid is missing (fail-closed). Default no-op bean is registered when no
 * other hook is present.
 */
public interface PrePublishHook {

    /** Returns content to publish (may be the same instance). */
    PostVariantContent beforePublish(PublicationClaim claim, PostVariantContent content);
}
