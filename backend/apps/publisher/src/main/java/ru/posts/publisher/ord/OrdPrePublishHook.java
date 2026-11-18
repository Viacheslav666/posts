package ru.posts.publisher.ord;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ru.posts.ord.api.AdMarker;
import ru.posts.persistence.jdbc.PublicationClaim;
import ru.posts.publisher.extension.PrePublishHook;
import ru.posts.social.api.ErrorClass;
import ru.posts.social.api.PostVariantContent;
import ru.posts.social.api.SocialApiException;

/**
 * BE-15 fail-closed ORD hook: advertising publications without erid never reach the network;
 * when erid is present, prepends the mandatory «Реклама… erid:…» marker.
 */
@Component
@Order(100)
public class OrdPrePublishHook implements PrePublishHook {

    private final boolean allowPublishWithoutErid;

    public OrdPrePublishHook(@Value("${posts.ord.allow-publish-without-erid:false}") boolean allowPublishWithoutErid) {
        this.allowPublishWithoutErid = allowPublishWithoutErid;
    }

    @Override
    public PostVariantContent beforePublish(PublicationClaim claim, PostVariantContent content) {
        if (claim == null || !claim.ad()) {
            return content;
        }
        String erid = claim.erid();
        if (erid == null || erid.isBlank()) {
            if (allowPublishWithoutErid) {
                return content;
            }
            throw new SocialApiException(
                    ErrorClass.PERMANENT, "ord_erid_missing: advertising publication blocked without erid", 422);
        }
        String marker = AdMarker.format(claim.advertiserName(), claim.advertiserInn(), erid);
        String text = content == null ? null : content.text();
        if (text != null && text.contains("erid:")) {
            return content;
        }
        if (content == null) {
            return new PostVariantContent(AdMarker.prepend(text, marker));
        }
        return content.withText(AdMarker.prepend(text, marker));
    }
}
