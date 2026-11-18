package ru.posts.publisher.ord;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ru.posts.ord.api.AdMarker;
import ru.posts.ord.api.MarkingPlacement;
import ru.posts.persistence.jdbc.PublicationClaim;
import ru.posts.publisher.extension.PrePublishHook;
import ru.posts.social.api.ErrorClass;
import ru.posts.social.api.PostVariantContent;
import ru.posts.social.api.SocialApiException;

/**
 * BE-15/BE-25 fail-closed ORD hook: advertising publications without erid never reach the network;
 * when erid is present, applies the configured placement mode (six modes, VK menu honest fallback).
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

        MarkingPlacement placement = MarkingPlacement.resolve(claim.markingPlacement(), claim.markingPlacement());
        boolean story = claim.contentKind() != null && claim.contentKind().equalsIgnoreCase("story");
        String linkUrl = content == null ? claim.storyLinkUrl() : content.linkUrl();
        if (linkUrl == null || linkUrl.isBlank()) {
            linkUrl = claim.storyLinkUrl();
        }

        AdMarker.ApplyResult applied = story
                ? AdMarker.applyForStory(text, marker, erid, placement, linkUrl)
                : AdMarker.apply(text, marker, erid, placement, claim.network(), linkUrl);

        if (content == null) {
            return new PostVariantContent(
                    applied.text(),
                    claim.mediaKind(),
                    claim.storyTtl(),
                    claim.storyLinkText(),
                    applied.linkUrl(),
                    applied.meta());
        }
        Map<String, Object> meta = new LinkedHashMap<>(content.meta());
        meta.putAll(applied.meta());
        if (!applied.warnings().isEmpty()) {
            meta.put("ordPlacementWarnings", applied.warnings());
        }
        return content.withText(applied.text()).withLinkUrl(applied.linkUrl()).withMeta(meta);
    }
}
