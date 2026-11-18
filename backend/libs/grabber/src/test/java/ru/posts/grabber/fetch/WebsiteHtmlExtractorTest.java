package ru.posts.grabber.fetch;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WebsiteHtmlExtractorTest {

    @Test
    void extractsOgFieldsAndSanitizesXss() {
        String html =
                """
                <html><head>
                  <meta property="og:title" content="Hello OG"/>
                  <meta property="og:description" content="Desc"/>
                  <meta property="og:image" content="https://cdn.example/a.jpg"/>
                  <meta property="og:url" content="https://news.example/posts/1"/>
                  <meta property="og:site_name" content="NewsEx"/>
                  <script>alert(1)</script>
                </head>
                <body>
                  <article>
                    <p>Safe paragraph</p>
                    <img src="https://cdn.example/b.jpg"/>
                    <a href="javascript:alert(1)">bad</a>
                    <p onclick="evil()">click</p>
                  </article>
                </body></html>
                """;
        SourceCandidate c = WebsiteHtmlExtractor.extract(
                "https://news.example/posts/1",
                html.getBytes(StandardCharsets.UTF_8),
                "text/html; charset=utf-8",
                null);
        assertThat(c.sourceNetwork()).isEqualTo("website");
        assertThat(c.sourcePermalink()).isEqualTo("https://news.example/posts/1");
        assertThat(c.sourceName()).isEqualTo("NewsEx");
        assertThat(c.contentText()).contains("Hello OG").contains("Safe paragraph");
        assertThat(c.contentHtml())
                .doesNotContain("script")
                .doesNotContain("onclick")
                .doesNotContain("javascript:");
        assertThat(c.attachments()).isNotEmpty();
        assertThat(c.remotePostId()).contains("news.example/posts/1");
    }

    @Test
    void usesCssSelectorWhenProvided() {
        String html =
                """
                <html><body>
                  <div class="noise">ignore</div>
                  <div class="post-body"><p>Selected body</p></div>
                </body></html>
                """;
        SourceCandidate c = WebsiteHtmlExtractor.extract(
                "https://blog.example/x", html.getBytes(StandardCharsets.UTF_8), "text/html", ".post-body");
        assertThat(c.contentText()).contains("Selected body");
        assertThat(c.contentText()).doesNotContain("ignore");
    }
}
