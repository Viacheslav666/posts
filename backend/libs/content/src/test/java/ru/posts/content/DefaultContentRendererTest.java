package ru.posts.content;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultContentRendererTest {

    private DefaultContentRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new DefaultContentRenderer();
    }

    @Test
    void plainTextWithoutMarkup() {
        ContentRenderResult result = renderer.render(
                new ContentRenderRequest("tg", "Hello Posts", false, new NetworkTextLimits(4096, 1024)));

        assertThat(result.html()).isEqualTo("Hello Posts");
        assertThat(result.plainText()).isEqualTo("Hello Posts");
        assertThat(result.valid()).isTrue();
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void boldKeptForTelegram() {
        ContentRenderResult result = renderer.render(new ContentRenderRequest("tg", "<b>bold</b>", false));

        assertThat(result.html()).isEqualTo("<b>bold</b>");
        assertThat(result.plainText()).isEqualTo("bold");
        assertThat(result.valid()).isTrue();
    }

    @Test
    void unknownTagsStrippedForTelegram() {
        ContentRenderResult result = renderer.render(new ContentRenderRequest("tg", "<div>keep</div>", false));

        assertThat(result.html()).isEqualTo("keep");
        assertThat(result.plainText()).isEqualTo("keep");
    }

    @Test
    void exceedsTelegramMaxChars() {
        String body = "x".repeat(4097);
        ContentRenderResult result =
                renderer.render(new ContentRenderRequest("tg", body, false, new NetworkTextLimits(4096, 1024)));

        assertThat(result.valid()).isFalse();
        assertThat(result.issues())
                .anyMatch(issue -> "text.max_chars".equals(issue.code()) && "error".equals(issue.severity()));
    }

    @Test
    void vkStripsFormattingWithWarning() {
        ContentRenderResult result = renderer.render(new ContentRenderRequest("vk", "<b>x</b>", false));

        assertThat(result.html()).isEqualTo("x");
        assertThat(result.plainText()).isEqualTo("x");
        assertThat(result.warnings()).contains("formatting.stripped");
        assertThat(result.issues()).anyMatch(issue -> "formatting.stripped".equals(issue.code()));
    }

    @Test
    void captionLimitUsedWhenHasMedia() {
        String body = "y".repeat(1025);
        ContentRenderResult result =
                renderer.render(new ContentRenderRequest("tg", body, true, new NetworkTextLimits(4096, 1024)));

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).anyMatch(issue -> "text.max_chars".equals(issue.code()));
    }
}
