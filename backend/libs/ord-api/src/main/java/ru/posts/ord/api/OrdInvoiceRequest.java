package ru.posts.ord.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Act / invoice submission payload. {@code items} hold provider-specific allocation rows. */
public record OrdInvoiceRequest(
        String externalId,
        String contractExternalId,
        LocalDate date,
        LocalDate dateStart,
        LocalDate dateEnd,
        String serial,
        BigDecimal amount,
        String vatRate,
        String clientRole,
        String contractorRole,
        List<Map<String, Object>> items) {

    public OrdInvoiceRequest {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
