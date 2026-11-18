package ru.posts.ord.reporting.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrdReportsExporterTest {

    @Test
    void csvContainsHeaderAndRow() {
        byte[] csv = OrdReportsExporter.toCsv(List.of(new OrdReportsExporter.Row(
                2026, 10, "test-erid-1", "creative:1", "VK pad", 42, "1500.00", "submitted", "")));
        String text = new String(csv, StandardCharsets.UTF_8);
        assertThat(text).contains("PeriodYear,PeriodMonth,Erid");
        assertThat(text).contains("test-erid-1");
        assertThat(text).contains("42");
    }

    @Test
    void xlsxIsZipPackage() {
        byte[] xlsx = OrdReportsExporter.toXlsx(List.of(new OrdReportsExporter.Row(
                2026, 10, "test-erid-1", "creative:1", "VK pad", 42, "1500.00", "submitted", "")));
        assertThat(xlsx.length).isGreaterThan(100);
        // PK\x03\x04 zip magic
        assertThat(xlsx[0]).isEqualTo((byte) 0x50);
        assertThat(xlsx[1]).isEqualTo((byte) 0x4b);
    }
}
