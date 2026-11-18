package ru.posts.publisher.extension;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ru.posts.persistence.jdbc.PublicationClaim;
import ru.posts.social.api.PostVariantContent;

/**
 * Default BE-15 placeholder. Additional hooks (ORD) are chained after this no-op; removing it is
 * unnecessary.
 */
@Component
@Order(0)
public class NoOpPrePublishHook implements PrePublishHook {

    @Override
    public PostVariantContent beforePublish(PublicationClaim claim, PostVariantContent content) {
        return content;
    }
}
