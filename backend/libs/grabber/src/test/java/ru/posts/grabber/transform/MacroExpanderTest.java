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

    @Test
    void keyTextBlocksMatchWholeWordsAndSubstrings() {
        MacroExpander.Context ctx = MacroExpander.Context.of("Src", "-1", "wall-1_2", "Это #юмор и прикол");
        String whole = MacroExpander.expand("[keyText]#юмор|прикол,да[/keyText]", ctx);
        assertThat(whole).isEqualTo("да");
        String longerWord = MacroExpander.expand("[keyText]приколы,нет[/keyText]", ctx);
        assertThat(longerWord).isEmpty();
        String entry = MacroExpander.expand("[keyTextEntry]прикол,нашёл[/keyTextEntry]", ctx);
        assertThat(entry).isEqualTo("нашёл");
        String missing = MacroExpander.expand("[keyTextEntryNotFound]скидка,нет скидки[/keyTextEntryNotFound]", ctx);
        assertThat(missing).isEqualTo("нет скидки");
        String present = MacroExpander.expand("[keyTextEntryNotFound]юмор,скрыто[/keyTextEntryNotFound]", ctx);
        assertThat(present).isEmpty();
    }

    @Test
    void keyTextReplacementCanContainPlaceholdersAndRand() {
        MacroExpander.Context ctx = MacroExpander.Context.of("Клуб", "-9", "wall-9_3", "пост про #юмор");
        String out =
                MacroExpander.expand("[keyText]#юмор,[name_of_the_source] [rand]a|b[/rand][/keyText]", ctx, bound -> 0);
        assertThat(out).isEqualTo("Клуб a");
    }
}
