package ru.posts.notifications.channel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class EmailTemplateRendererTest {

    @Test
    void bodyIsHtmlEscaped() {
        EmailTemplateRenderer renderer = new EmailTemplateRenderer();
        String html = renderer.render(
                "unknown.event",
                Map.of(
                        "title",
                        "Заголовок",
                        "body",
                        "<script>alert(1)</script> & \"quote\"",
                        "unsubscribeUrl",
                        "https://posts.example/unsub?x=1"));
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt; &amp; &quot;quote&quot;");
        assertThat(html).doesNotContain("<script>");
        assertThat(html).contains("https://posts.example/unsub?x=1");
    }
}
