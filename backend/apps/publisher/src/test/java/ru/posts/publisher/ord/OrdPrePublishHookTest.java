package ru.posts.publisher.ord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.posts.persistence.jdbc.PublicationClaim;
import ru.posts.social.api.ErrorClass;
import ru.posts.social.api.PostVariantContent;
import ru.posts.social.api.SocialApiException;

class OrdPrePublishHookTest {

    @Test
    void nonAdPassesThrough() {
        OrdPrePublishHook hook = new OrdPrePublishHook(false);
        PublicationClaim claim = claim(false, null, null, null);
        PostVariantContent content = new PostVariantContent("hello");
        assertThat(hook.beforePublish(claim, content)).isSameAs(content);
    }

    @Test
    void adWithoutEridFailClosed() {
        OrdPrePublishHook hook = new OrdPrePublishHook(false);
        PublicationClaim claim = claim(true, null, "ООО Тест", "7707083893");
        assertThatThrownBy(() -> hook.beforePublish(claim, new PostVariantContent("hello")))
                .isInstanceOf(SocialApiException.class)
                .satisfies(ex -> {
                    SocialApiException social = (SocialApiException) ex;
                    assertThat(social.errorClass()).isEqualTo(ErrorClass.PERMANENT);
                    assertThat(social.getMessage()).contains("ord_erid_missing");
                });
    }

    @Test
    void adWithEridPrependsMarker() {
        OrdPrePublishHook hook = new OrdPrePublishHook(false);
        PublicationClaim claim = claim(true, "test-erid-hook-0001", "ООО Тест", "7707083893");
        PostVariantContent out = hook.beforePublish(claim, new PostVariantContent("Текст поста"));
        assertThat(out.text()).startsWith("Реклама. ООО Тест. ИНН 7707083893. erid: test-erid-hook-0001");
        assertThat(out.text()).endsWith("Текст поста");
    }

    @Test
    void featureFlagAllowsPublishWithoutErid() {
        OrdPrePublishHook hook = new OrdPrePublishHook(true);
        PublicationClaim claim = claim(true, null, "ООО Тест", "7707083893");
        PostVariantContent content = new PostVariantContent("hello");
        assertThat(hook.beforePublish(claim, content).text()).isEqualTo("hello");
    }

    private static PublicationClaim claim(boolean ad, String erid, String name, String inn) {
        return new PublicationClaim(
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "vk",
                "idem",
                "-1",
                "text",
                null,
                1,
                "post",
                "{}",
                ad,
                "manual",
                erid,
                name,
                inn,
                30,
                true,
                null,
                null,
                null,
                "photo");
    }
}
