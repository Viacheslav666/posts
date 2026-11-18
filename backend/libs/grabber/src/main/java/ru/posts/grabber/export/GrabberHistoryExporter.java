package ru.posts.grabber.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** CSV / minimal XLSX export for grabber item history (BE-27). */
public final class GrabberHistoryExporter {

    public record Row(
            String groupOrRuleName,
            String sourcePostId,
            String sourceId,
            String publishedPostId,
            Instant detectedAt,
            String status,
            String skipReason,
            String permalink) {}

    private GrabberHistoryExporter() {}

    public static byte[] toCsv(List<Row> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            w.write('\ufeff'); // BOM for Excel
            w.write("Group,SourcePostId,SourceId,PublishedPostId,DetectedAt,Status,SkipReason,Permalink\n");
            for (Row row : rows) {
                w.write(csv(row.groupOrRuleName()));
                w.write(',');
                w.write(csv(row.sourcePostId()));
                w.write(',');
                w.write(csv(row.sourceId()));
                w.write(',');
                w.write(csv(row.publishedPostId()));
                w.write(',');
                w.write(csv(row.detectedAt() == null ? "" : row.detectedAt().toString()));
                w.write(',');
                w.write(csv(row.status()));
                w.write(',');
                w.write(csv(row.skipReason()));
                w.write(',');
                w.write(csv(row.permalink()));
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
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                    <sheet name="History" sheetId="1" r:id="rId1"/>
                  </sheets>
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
        sb.append(rowXml(
                1,
                List.of(
                        "Group",
                        "SourcePostId",
                        "SourceId",
                        "PublishedPostId",
                        "DetectedAt",
                        "Status",
                        "SkipReason",
                        "Permalink")));
        int r = 2;
        for (Row row : rows) {
            sb.append(rowXml(
                    r++,
                    List.of(
                            nullToEmpty(row.groupOrRuleName()),
                            nullToEmpty(row.sourcePostId()),
                            nullToEmpty(row.sourceId()),
                            nullToEmpty(row.publishedPostId()),
                            row.detectedAt() == null ? "" : row.detectedAt().toString(),
                            nullToEmpty(row.status()),
                            nullToEmpty(row.skipReason()),
                            nullToEmpty(row.permalink()))));
        }
        sb.append("""
                  </sheetData>
                </worksheet>
                """);
        return sb.toString();
    }

    private static String rowXml(int rowNum, List<String> cells) {
        StringBuilder sb = new StringBuilder();
        sb.append("<row r=\"").append(rowNum).append("\">");
        for (int i = 0; i < cells.size(); i++) {
            String ref = colName(i) + rowNum;
            sb.append("<c r=\"")
                    .append(ref)
                    .append("\" t=\"inlineStr\"><is><t>")
                    .append(xml(cells.get(i)))
                    .append("</t></is></c>");
        }
        sb.append("</row>");
        return sb.toString();
    }

    private static String colName(int index) {
        return Character.toString((char) ('A' + index));
    }

    private static String csv(String value) {
        String v = value == null ? "" : value;
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private static String xml(String value) {
        return value == null
                ? ""
                : value.replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\"", "&quot;");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
