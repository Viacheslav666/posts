package ru.posts.grabber.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class GrabberHistoryExporterTest {

    @Test
    void csvContainsBomAndHeader() {
        byte[] csv = GrabberHistoryExporter.toCsv(List.of(new GrabberHistoryExporter.Row(
                "Правило",
                "wall-1_2",
                "-1",
                "post-uuid",
                Instant.parse("2026-11-18T10:00:00Z"),
                "published",
                null,
                "https://vk.com/wall-1_2")));
        String text = new String(csv, StandardCharsets.UTF_8);
        assertThat(text).startsWith("\ufeffGroup,");
        assertThat(text).contains("wall-1_2").contains("Правило");
    }

    @Test
    void xlsxIsValidZipWithSheet() throws Exception {
        byte[] xlsx = GrabberHistoryExporter.toXlsx(List.of(new GrabberHistoryExporter.Row(
                "R",
                "wall-1_2",
                "-1",
                null,
                Instant.parse("2026-11-18T10:00:00Z"),
                "filtered_out",
                "exclude_ads",
                "")));
        assertThat(xlsx[0]).isEqualTo((byte) 'P'); // PK zip magic
        try (ZipInputStream zin = new ZipInputStream(new java.io.ByteArrayInputStream(xlsx))) {
            assertThat(zin.getNextEntry().getName()).isEqualTo("[Content_Types].xml");
            boolean foundSheet = false;
            var entry = zin.getNextEntry();
            while (entry != null) {
                if ("xl/worksheets/sheet1.xml".equals(entry.getName())) {
                    foundSheet = true;
                }
                entry = zin.getNextEntry();
            }
            assertThat(foundSheet).isTrue();
        }
    }
}
