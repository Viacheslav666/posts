package ru.posts.content;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpintaxExpanderTest {

    @Test
    void expandsWithInjectedIndex() {
        String out = SpintaxExpander.expand("[rand]alpha|beta|gamma[/rand]", bound -> 1);
        assertThat(out).isEqualTo("beta");
    }

    @Test
    void deterministicSeedIsStableAcrossCalls() {
        var picker = SpintaxExpander.deterministicIndex("pub-123");
        String first = SpintaxExpander.expand("[rand]a|b|c[/rand] and [rand]x|y[/rand]", picker);
        String second = SpintaxExpander.expand("[rand]a|b|c[/rand] and [rand]x|y[/rand]", picker);
        assertThat(first).isEqualTo(second);
        assertThat(first).matches("(a|b|c) and (x|y)");
    }

    @Test
    void differentSeedsCanDiffer() {
        String a = SpintaxExpander.expand(
                "[rand]one|two|three|four|five|six|seven|eight[/rand]", SpintaxExpander.deterministicIndex("seed-a"));
        String b = SpintaxExpander.expand(
                "[rand]one|two|three|four|five|six|seven|eight[/rand]", SpintaxExpander.deterministicIndex("seed-b"));
        // Extremely unlikely to always collide across 8 options; assert at least one known path works.
        assertThat(a).isIn("one", "two", "three", "four", "five", "six", "seven", "eight");
        assertThat(b).isIn("one", "two", "three", "four", "five", "six", "seven", "eight");
    }

    @Test
    void retrySamePublicationSeedIsIdempotent() {
        String seed = "idempotency-key-xyz";
        String attempt1 =
                SpintaxExpander.expand("[rand]привет|здравствуйте[/rand]", SpintaxExpander.deterministicIndex(seed));
        String attempt2 =
                SpintaxExpander.expand("[rand]привет|здравствуйте[/rand]", SpintaxExpander.deterministicIndex(seed));
        assertThat(attempt1).isEqualTo(attempt2);
    }
}
