package ru.posts.grabber.sanitize;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HtmlSanitizerTest {

    @Test
    void stripsScriptsAndEventHandlers() {
        String dirty =
                "<p>Hi</p><script>alert(1)</script><img src=x onerror=alert(1)><a href=\"javascript:alert(1)\">x</a>";
        String clean = HtmlSanitizer.sanitize(dirty);
        assertThat(clean).contains("<p>Hi</p>");
        assertThat(clean).doesNotContain("script").doesNotContain("onerror").doesNotContain("javascript:");
    }

    @Test
    void plainTextStripsTags() {
        assertThat(HtmlSanitizer.toPlainText("<b>Bold</b> &amp; text")).isEqualTo("Bold & text");
    }
}
