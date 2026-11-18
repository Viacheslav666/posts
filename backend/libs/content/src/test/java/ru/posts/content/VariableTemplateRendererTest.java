package ru.posts.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VariableTemplateRendererTest {

    @Test
    void rendersDateTimeInPostTimezone() {
        Instant at = Instant.parse("2026-11-18T10:30:00Z"); // 13:30 Moscow
        String out = VariableTemplateRenderer.render(
                "Сегодня {date} {time}, {weekday}, {day} {month} {year}",
                VariableTemplateRenderer.Context.builder()
                        .at(at)
                        .zone("Europe/Moscow")
                        .build());
        assertThat(out).isEqualTo("Сегодня 18.11.2026 13:30, среда, 18 ноября 2026");
    }

    @Test
    void differentTimezonesChangeTimeAndPossiblyDate() {
        Instant at = Instant.parse("2026-11-18T22:30:00Z");
        String moscow = VariableTemplateRenderer.render(
                "{date} {time}",
                VariableTemplateRenderer.Context.builder()
                        .at(at)
                        .zone("Europe/Moscow")
                        .build());
        String utc = VariableTemplateRenderer.render(
                "{date} {time}",
                VariableTemplateRenderer.Context.builder().at(at).zone("UTC").build());
        assertThat(moscow).isEqualTo("19.11.2026 01:30");
        assertThat(utc).isEqualTo("18.11.2026 22:30");
    }

    @Test
    void rendersGroupAndBirthdays() {
        String out = VariableTemplateRenderer.render(
                "{group_name} ({group_id}): {birthdays} / {именинники}",
                VariableTemplateRenderer.Context.builder()
                        .groupId("-123")
                        .groupName("Моя группа")
                        .birthdays("Иван Иванов")
                        .build());
        assertThat(out).isEqualTo("Моя группа (-123): Иван Иванов / Иван Иванов");
    }

    @Test
    void hyperlinkPlainAndHtml() {
        String plain = VariableTemplateRenderer.render(
                "{hyperlink:Открыть|https://example.com}",
                VariableTemplateRenderer.Context.builder().htmlNetwork(false).build());
        String html = VariableTemplateRenderer.render(
                "{link:Открыть|https://example.com}",
                VariableTemplateRenderer.Context.builder().htmlNetwork(true).build());
        assertThat(plain).isEqualTo("Открыть (https://example.com)");
        assertThat(html).isEqualTo("<a href=\"https://example.com\">Открыть</a>");
    }

    @Test
    void jsonPathLookup() {
        String out = VariableTemplateRenderer.render(
                "price={json:offer.price}",
                VariableTemplateRenderer.Context.builder()
                        .json(Map.of("offer", Map.of("price", "199")))
                        .build());
        assertThat(out).isEqualTo("price=199");
    }

    @Test
    void spintaxDeterministicWithSeed() {
        var ctx =
                VariableTemplateRenderer.Context.builder().spintaxSeed("pub-1").build();
        String a = VariableTemplateRenderer.render("[rand]один|два|три[/rand]", ctx);
        String b = VariableTemplateRenderer.render("[rand]один|два|три[/rand]", ctx);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void birthdayDateMatch() {
        assertThat(VariableTemplateRenderer.isBirthdayToday("18.11", LocalDate.of(2026, 11, 18)))
                .isTrue();
        assertThat(VariableTemplateRenderer.isBirthdayToday("18.11.1990", LocalDate.of(2026, 11, 18)))
                .isTrue();
        assertThat(VariableTemplateRenderer.isBirthdayToday("19.11", LocalDate.of(2026, 11, 18)))
                .isFalse();
    }
}
