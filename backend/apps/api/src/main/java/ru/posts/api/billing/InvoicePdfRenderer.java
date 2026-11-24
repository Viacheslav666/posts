package ru.posts.api.billing;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import ru.posts.persistence.entity.InvoiceEntity;

/**
 * Minimal PDF generator (no external deps): produces a valid single-page PDF with invoice text.
 */
@Component
public class InvoicePdfRenderer {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.of("Europe/Moscow"));

    public byte[] renderInvoice(InvoiceEntity invoice) {
        return render("СЧЁТ", invoice, false);
    }

    public byte[] renderAct(InvoiceEntity invoice) {
        return render("АКТ", invoice, true);
    }

    private byte[] render(String title, InvoiceEntity invoice, boolean act) {
        Map<String, Object> req = invoice.getBuyerRequisites() == null ? Map.of() : invoice.getBuyerRequisites();
        StringBuilder text = new StringBuilder();
        text.append(title)
                .append(' ')
                .append(invoice.getInvoiceNumber() == null ? invoice.getId() : invoice.getInvoiceNumber())
                .append('\n');
        text.append("Дата: ")
                .append(DATE.format(invoice.getCreatedAt() == null ? Instant.now() : invoice.getCreatedAt()))
                .append('\n');
        text.append("Сумма: ")
                .append(MoneyKopecks.formatRubles(invoice.getAmountKopecks()))
                .append(" RUB\n");
        text.append("Покупатель: ").append(str(req.get("name"))).append('\n');
        text.append("ИНН: ").append(str(req.get("inn")));
        if (req.get("kpp") != null) {
            text.append("  КПП: ").append(str(req.get("kpp")));
        }
        text.append('\n');
        if (req.get("ogrn") != null) {
            text.append("ОГРН: ").append(str(req.get("ogrn"))).append('\n');
        }
        if (act) {
            text.append("Услуги: доступ к сервису Posts (пополнение баланса).\n");
            text.append("ЭДО ФНС 970 - следующий этап (не входит в этот релиз).\n");
        } else {
            text.append("Назначение: пополнение баланса workspace в Posts.\n");
            text.append("Оплата по счёту подтверждается администратором вручную.\n");
        }
        return toPdf(text.toString());
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString();
    }

    /** Builds a minimal PDF 1.4 document with Helvetica text lines. */
    static byte[] toPdf(String body) {
        String normalized = body.replace('\r', ' ');
        List<String> lines = new ArrayList<>();
        int start = 0;
        while (start <= normalized.length()) {
            int nl = normalized.indexOf('\n', start);
            if (nl < 0) {
                lines.add(normalized.substring(start));
                break;
            }
            lines.add(normalized.substring(start, nl));
            start = nl + 1;
        }
        StringBuilder content = new StringBuilder("BT /F1 11 Tf 50 780 Td 14 TL\n");
        for (int i = 0; i < lines.size(); i++) {
            String line = escapePdf(lines.get(i));
            if (i == 0) {
                content.append("(").append(line).append(") Tj\n");
            } else {
                content.append("T*\n(").append(line).append(") Tj\n");
            }
        }
        content.append("ET");
        byte[] stream = content.toString().getBytes(StandardCharsets.ISO_8859_1);

        StringBuilder pdf = new StringBuilder();
        pdf.append("%PDF-1.4\n");
        int[] offsets = new int[6];
        offsets[1] = pdf.length();
        pdf.append("1 0 obj<< /Type /Catalog /Pages 2 0 R >>endobj\n");
        offsets[2] = pdf.length();
        pdf.append("2 0 obj<< /Type /Pages /Kids [3 0 R] /Count 1 >>endobj\n");
        offsets[3] = pdf.length();
        pdf.append(
                "3 0 obj<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>endobj\n");
        offsets[4] = pdf.length();
        pdf.append("4 0 obj<< /Length ").append(stream.length).append(" >>stream\n");
        pdf.append(content);
        pdf.append("\nendstream\nendobj\n");
        offsets[5] = pdf.length();
        pdf.append("5 0 obj<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>endobj\n");
        int xref = pdf.length();
        pdf.append("xref\n0 6\n");
        pdf.append("0000000000 65535 f \n");
        for (int i = 1; i <= 5; i++) {
            pdf.append(String.format("%010d 00000 n \n", offsets[i]));
        }
        pdf.append("trailer<< /Size 6 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF");
        return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private static String escapePdf(String s) {
        return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }
}
