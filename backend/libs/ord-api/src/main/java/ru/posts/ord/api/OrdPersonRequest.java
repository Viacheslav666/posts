package ru.posts.ord.api;

import java.util.List;
import java.util.Map;

/**
 * Counterparty / organization upsert payload.
 *
 * <p>{@code orgType}: {@code physical}, {@code ip}, {@code juridical}, {@code foreign_physical},
 * {@code foreign_juridical}.
 *
 * <p>{@code foreignFields} may carry provider-agnostic keys such as {@code foreign_inn}, {@code
 * foreign_reg_number}, {@code foreign_epayment}.
 */
public record OrdPersonRequest(
        String externalId,
        String name,
        String orgType,
        String inn,
        String kpp,
        String phone,
        String oksmCountry,
        List<String> roles,
        Map<String, String> foreignFields) {

    public OrdPersonRequest {
        roles = roles == null ? List.of() : List.copyOf(roles);
        foreignFields = foreignFields == null ? Map.of() : Map.copyOf(foreignFields);
    }
}
