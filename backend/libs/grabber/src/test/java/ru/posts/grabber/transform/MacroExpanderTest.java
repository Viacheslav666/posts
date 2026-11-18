package ru.posts.grabber.transform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MacroExpanderTest {

    @Test
    void expandsSourceMacros() {
        String out = MacroExpander.expand(
                "[name_of_the_source]/[id_of_the_source]/[post_id_of_the_source]",
                MacroExpander.Context.of("Новости", "-42", "wall-42_7"));
        assertThat(out).isEqualTo("Новости/-42/wall-42_7");
    }

    @Test
    void expandsRandDeterministically() {
        String out =
                MacroExpander.expand("[rand]alpha|beta|gamma[/rand]", MacroExpander.Context.of("", "", ""), bound -> 1);
        assertThat(out).isEqualTo("beta");
    }

    @Test
    void expandsKeyTextVariants() {
        MacroExpander.Context ctx = new MacroExpander.Context("n", "i", "p", "KEY", "ENTRY", "NF");
        String out = MacroExpander.expand("[keyText]-[keyTextEntry]-[keyTextEntryNotFound]", ctx);
        assertThat(out).isEqualTo("KEY-ENTRY-NF");
    }
}
