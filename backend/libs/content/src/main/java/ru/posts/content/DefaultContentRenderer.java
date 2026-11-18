package ru.posts.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Default network-specific HTML filter and text-limit validation. */
public final class DefaultContentRenderer implements ContentRenderer {

    private static final Set<String> HTML_NETWORKS = Set.of("tg", "max");
    private static final Set<String> PLAIN_NETWORKS = Set.of("vk", "ok", "rutube");

    @Override
    public ContentRenderResult render(ContentRenderRequest request) {
        String network = normalizeNetwork(request.network());
        String input = request.htmlOrText() == null ? "" : request.htmlOrText();
        NetworkTextLimits limits = request.limits() == null ? new NetworkTextLimits(null, null) : request.limits();

        String html;
        String plainText;
        List<String> warnings = new ArrayList<>();
        List<ContentValidationIssue> issues = new ArrayList<>();

        boolean hadFormatting = HtmlContentSupport.hasMarkupTags(input);

        if (HTML_NETWORKS.contains(network)) {
            html = HtmlContentSupport.filterAllowedTags(input);
            plainText = HtmlContentSupport.toPlainText(html);
        } else if (PLAIN_NETWORKS.contains(network)) {
            plainText = HtmlContentSupport.toPlainText(input);
            html = plainText;
            if (hadFormatting) {
                issues.add(new ContentValidationIssue(
                        "formatting.stripped",
                        "warning",
                        "Formatting will be stripped for this network (plain text only).",
                        "text"));
                warnings.add("formatting.stripped");
            }
        } else {
            plainText = HtmlContentSupport.toPlainText(input);
            html = plainText;
        }

        Integer maxAllowed = resolveMaxChars(request.hasMedia(), limits);
        if (maxAllowed != null) {
            int length = HtmlContentSupport.plainLength(plainText);
            if (length > maxAllowed) {
                issues.add(new ContentValidationIssue(
                        "text.max_chars",
                        "error",
                        "Text exceeds maximum length of " + maxAllowed + " characters (" + length + ").",
                        "text"));
            }
        }

        boolean valid = issues.stream().noneMatch(issue -> "error".equals(issue.severity()));

        return new ContentRenderResult(network, html, plainText, valid, List.copyOf(warnings), List.copyOf(issues));
    }

    private static String normalizeNetwork(String network) {
        if (network == null) {
            return "";
        }
        return network.trim().toLowerCase(Locale.ROOT);
    }

    private static Integer resolveMaxChars(boolean hasMedia, NetworkTextLimits limits) {
        if (hasMedia && limits.captionMaxChars() != null) {
            return limits.captionMaxChars();
        }
        return limits.maxChars();
    }
}
