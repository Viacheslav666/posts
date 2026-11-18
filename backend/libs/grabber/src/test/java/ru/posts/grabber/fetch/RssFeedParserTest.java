package ru.posts.grabber.fetch;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class RssFeedParserTest {

    @Test
    void parsesRss2() {
        String xml =
                """
                <?xml version="1.0"?>
                <rss version="2.0"><channel>
                  <title>Feed</title>
                  <item>
                    <title>Hello</title>
                    <description>World</description>
                    <guid>g-1</guid>
                    <link>https://example.com/1</link>
                  </item>
                </channel></rss>
                """;
        List<SourceCandidate> items =
                RssFeedParser.parse("https://example.com/feed.xml", xml.getBytes(StandardCharsets.UTF_8), 10);
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().remotePostId()).isEqualTo("g-1");
        assertThat(items.getFirst().contentText()).contains("Hello");
    }
}
