package ru.posts.ord.api;

import java.util.List;

/**
 * Creative registration payload. Idempotent by {@code externalId}.
 *
 * <p>Self-promo creatives use {@code selfPromoOrgExternalId} instead of contracts.
 */
public record OrdCreativeRequest(
        String externalId,
        List<String> contractExternalIds,
        String name,
        String description,
        List<String> kktuCodes,
        List<String> texts,
        String form,
        boolean selfPromo,
        String selfPromoOrgExternalId,
        List<String> targetUrls,
        String brand,
        String category,
        String payType,
        List<String> mediaExternalIds) {

    public OrdCreativeRequest {
        contractExternalIds = contractExternalIds == null ? List.of() : List.copyOf(contractExternalIds);
        kktuCodes = kktuCodes == null ? List.of() : List.copyOf(kktuCodes);
        texts = texts == null ? List.of() : List.copyOf(texts);
        targetUrls = targetUrls == null ? List.of() : List.copyOf(targetUrls);
        mediaExternalIds = mediaExternalIds == null ? List.of() : List.copyOf(mediaExternalIds);
    }
}
