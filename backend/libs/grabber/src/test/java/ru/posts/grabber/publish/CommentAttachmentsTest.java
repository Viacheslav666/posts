package ru.posts.grabber.publish;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.posts.grabber.transform.MacroExpander;

class CommentAttachmentsTest {

    @Test
    void expandsRandAndRejectsUnknownTokens() {
        CommentAttachments.ParseResult parsed = CommentAttachments.parse(
                "[rand]photo-1_2|video-3_4[/rand], nope", Map.of(), MacroExpander.Context.of("", "", "", ""));
        assertThat(parsed.valid()).containsAnyOf("photo-1_2", "video-3_4");
        assertThat(parsed.invalid()).contains("nope");
    }

    @Test
    void invalidTemplateTokens() {
        assertThat(CommentAttachments.invalidTokens("photo-1_2, [rand]video-9_8|story-1[/rand]"))
                .containsExactly("story-1");
        assertThat(CommentAttachments.invalidTokens("photo123_456")).isEmpty();
    }
}
