package ru.posts.ord.reporting.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** CSV / minimal XLSX export for ORD reporting table (BE-26). */
public final class OrdReportsExporter {

    public record Row(
            int periodYear,
            int periodMonth,
            String erid,
            String creativeExternalId,
            String platform,
            long impressions,
            String amount,
            String status,
            String error) {}

    private OrdReportsExporter() {}

    public static byte[] toCsv(List<Row> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            w.write('\ufeff');
            w.write("PeriodYear,PeriodMonth,Erid,CreativeExternalId,Platform,Impressions,Amount,Status,Error\n");
            for (Row row : rows) {
                w.write(csv(String.valueOf(row.periodYear())));
                w.write(',');
                w.write(csv(String.valueOf(row.periodMonth())));
                w.write(',');
                w.write(csv(row.erid()));
                w.write(',');
                w.write(csv(row.creativeExternalId()));
                w.write(',');
                w.write(csv(row.platform()));
                w.write(',');
                w.write(csv(String.valueOf(row.impressions())));
                w.write(',');
                w.write(csv(row.amount()));
                w.write(',');
                w.write(csv(row.status()));
                w.write(',');
                w.write(csv(row.error()));
                w.write('\n');
            }
        } catch (IOException ex) {
            throw new IllegalStateException("CSV export failed", ex);
        }
        return out.toByteArray();
    }

    public static byte[] toXlsx(List<Row> rows) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                write(zip, "[Content_Types].xml", contentTypes());
                write(zip, "_rels/.rels", rootRels());
                write(zip, "xl/workbook.xml", workbook());
                write(zip, "xl/_rels/workbook.xml.rels", workbookRels());
                write(zip, "xl/worksheets/sheet1.xml", sheet(rows));
            }
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("XLSX export failed", ex);
        }
    }

    private static void write(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String contentTypes() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                </Types>
                """;
    }

    private static String rootRels() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>
                """;
    }

    private static String workbook() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets><sheet name="ORD Reports" sheetId="1" r:id="rId1"/></sheets>
                </workbook>
                """;
    }

    private static String workbookRels() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                </Relationships>
                """;
    }

    private static String sheet(List<Row> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                """);
        sb.append("<row>")
                .append(cell("PeriodYear"))
                .append(cell("PeriodMonth"))
                .append(cell("Erid"))
                .append(cell("CreativeExternalId"))
                .append(cell("Platform"))
                .append(cell("Impressions"))
                .append(cell("Amount"))
                .append(cell("Status"))
                .append(cell("Error"))
                .append("</row>");
        for (Row row : rows) {
            sb.append("<row>")
                    .append(cell(String.valueOf(row.periodYear())))
                    .append(cell(String.valueOf(row.periodMonth())))
                    .append(cell(nullToEmpty(row.erid())))
                    .append(cell(nullToEmpty(row.creativeExternalId())))
                    .append(cell(nullToEmpty(row.platform())))
                    .append(cell(String.valueOf(row.impressions())))
                    .append(cell(nullToEmpty(row.amount())))
                    .append(cell(nullToEmpty(row.status())))
                    .append(cell(nullToEmpty(row.error())))
                    .append("</row>");
        }
        sb.append("</sheetData></worksheet>");
        return sb.toString();
    }

    private static String cell(String value) {
        return "<c t=\"inlineStr\"><is><t>" + xml(value) + "</t></is></c>";
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String xml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
