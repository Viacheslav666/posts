package ru.posts.publisher.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.posts.persistence.jdbc.PublicationClaim;
import ru.posts.social.api.PostVariantContent;
import ru.posts.social.api.SocialAdapter;

class VariableRenderPrePublishHookTest {

    @Test
    void expandsVariablesAndDeterministicSpintax() {
        SocialAdapter adapter = mock(SocialAdapter.class);
        when(adapter.resolveBirthdays(any(), any(), anyString())).thenReturn("Иван");
        VariableRenderPrePublishHook hook = new VariableRenderPrePublishHook(adapter);

        PublicationClaim claim = claim(
                "Hello {group_name} {date} [rand]alpha|beta|gamma[/rand] {birthdays}",
                "idem-stable-key",
                Instant.parse("2026-11-18T10:30:00Z"));
        PostVariantContent out = hook.beforePublish(claim, new PostVariantContent(claim.contentText()));
        String first = out.text();
        String second = hook.beforePublish(claim, new PostVariantContent(claim.contentText()))
                .text();

        assertThat(first).contains("Моя группа");
        assertThat(first).contains("18.11.2026");
        assertThat(first).contains("Иван");
        assertThat(first).doesNotContain("[rand]");
        assertThat(first).isEqualTo(second);
    }

    private static PublicationClaim claim(String text, String idem, Instant runAt) {
        return new PublicationClaim(
                UUID.randomUUID(),
                runAt,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "vk",
                idem,
                "-100",
                text,
                null,
                1,
                "post",
                "{}",
                false,
                "manual",
                null,
                null,
                null,
                null,
                true,
                null,
                null,
                null,
                "photo",
                "[]",
                "Europe/Moscow",
                "Моя группа",
                "[]",
                null);
    }
}
