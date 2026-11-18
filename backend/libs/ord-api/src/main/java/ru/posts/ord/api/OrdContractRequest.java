package ru.posts.ord.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Contract upsert payload.
 *
 * <p>{@code type}: provider-neutral values such as {@code service}, {@code mediation}, {@code
 * additional} (adapters map to vendor enums).
 */
public record OrdContractRequest(
        String externalId,
        String clientExternalId,
        String contractorExternalId,
        String type,
        String serial,
        LocalDate dateStart,
        LocalDate dateEnd,
        BigDecimal amount,
        String vatRate,
        boolean selfPromo,
        String subjectType,
        String actionType,
        String parentContractExternalId,
        List<String> flags) {

    public OrdContractRequest {
        flags = flags == null ? List.of() : List.copyOf(flags);
    }
}
