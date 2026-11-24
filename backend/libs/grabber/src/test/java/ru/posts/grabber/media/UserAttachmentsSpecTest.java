package ru.posts.grabber.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class UserAttachmentsSpecTest {

    private static RandomGenerator fixed(int value) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return value;
            }

            @Override
            public int nextInt(int bound) {
                return Math.min(value, bound - 1);
            }
        };
    }

    @Test
    void blankSpecIsValidAndEmpty() {
        assertThat(UserAttachmentsSpec.validate("")).isEmpty();
        assertThat(UserAttachmentsSpec.validate(null)).isEmpty();
        assertThat(UserAttachmentsSpec.resolve(" ", "text", fixed(0))).isEmpty();
    }

    @Test
    void plainCommaSeparatedList() {
        String spec = "photo-1_2, video-1_3,photo-1_2";
        assertThat(UserAttachmentsSpec.validate(spec)).isEmpty();
        assertThat(UserAttachmentsSpec.resolve(spec, "", fixed(0))).containsExactly("photo-1_2", "video-1_3");
    }

    @Test
    void randPicksOneVariant() {
        String spec = "[rand]photo-1_2|photo-1_3,video-1_4[/rand]";
        assertThat(UserAttachmentsSpec.validate(spec)).isEmpty();
        assertThat(UserAttachmentsSpec.resolve(spec, "", fixed(0))).containsExactly("photo-1_2");
        assertThat(UserAttachmentsSpec.resolve(spec, "", fixed(1))).containsExactly("photo-1_3", "video-1_4");
    }

    @Test
    void keyTextEntryMatchesStemsCaseInsensitive() {
        String spec = "[keyTextEntry]photo-1_10,photo-1_11|прикол,смеят[/keyTextEntry]";
        assertThat(UserAttachmentsSpec.validate(spec)).isEmpty();
        assertThat(UserAttachmentsSpec.resolve(spec, "Все СМЕЯТСЯ до слёз", fixed(0)))
                .containsExactly("photo-1_10", "photo-1_11");
        assertThat(UserAttachmentsSpec.resolve(spec, "Серьёзная новость", fixed(0)))
                .isEmpty();
    }

    @Test
    void combinedBlocksAndPlainItems() {
        String spec = "video-5_5, [rand]photo-1_1|photo-1_2[/rand], [keyTextEntry]doc-1_9|отчёт[/keyTextEntry]";
        assertThat(UserAttachmentsSpec.validate(spec)).isEmpty();
        assertThat(UserAttachmentsSpec.resolve(spec, "Годовой отчёт", fixed(1)))
                .containsExactly("video-5_5", "photo-1_2", "doc-1_9");
    }

    @Test
    void reportsInvalidTokensAndSyntax() {
        assertThat(UserAttachmentsSpec.validate("photo-1_2, кот"))
                .singleElement()
                .asString()
                .contains("«кот»");
        assertThat(UserAttachmentsSpec.validate("[rand]photo-1_2[/rand]"))
                .anyMatch(e -> e.contains("минимум два варианта"));
        assertThat(UserAttachmentsSpec.validate("[keyTextEntry]photo-1_2[/keyTextEntry]"))
                .anyMatch(e -> e.contains("[keyTextEntry]"));
        assertThat(UserAttachmentsSpec.validate("[keyTextEntry]photo-1_2|[/keyTextEntry]"))
                .anyMatch(e -> e.contains("ключевые слова"));
        assertThat(UserAttachmentsSpec.validate("[rand]photo-1_2|photo-1_3")).anyMatch(e -> e.contains("Незакрытый"));
        assertThat(UserAttachmentsSpec.validate("x".repeat(UserAttachmentsSpec.MAX_LENGTH + 1)))
                .hasSize(1);
    }

    @Test
    void resolveCapsAtVkLimit() {
        StringBuilder spec = new StringBuilder();
        for (int i = 1; i <= 12; i++) {
            spec.append("photo-1_").append(i).append(',');
        }
        List<String> out = UserAttachmentsSpec.resolve(spec.toString(), "", fixed(0));
        assertThat(out).hasSize(GrabbedAttachments.VK_MAX_ATTACHMENTS);
    }
}
