package ru.posts.api.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.posts.persistence.entity.InvoiceEntity;

class InvoicePdfRendererTest {

    @Test
    void rendersPdfHeader() {
        InvoiceEntity invoice = new InvoiceEntity();
        invoice.setId(UUID.randomUUID());
        invoice.setWorkspaceId(UUID.randomUUID());
        invoice.setAmount(new BigDecimal("1000.00"));
        invoice.setAmountKopecks(100_000L);
        invoice.setCurrency("RUB");
        invoice.setStatus("issued");
        invoice.setInvoiceNumber("INV-1");
        invoice.setBuyerRequisites(Map.of("name", "ООО Тест", "inn", "7707083893", "kpp", "773601001"));
        invoice.setCreatedAt(Instant.parse("2026-11-18T10:00:00Z"));

        byte[] pdf = new InvoicePdfRenderer().renderInvoice(invoice);
        assertThat(pdf).isNotEmpty();
        String asText = new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertThat(asText).startsWith("%PDF-1.4");
        assertThat(asText).contains("%%EOF");
    }
}
