package ru.posts.crosspost.loop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.posts.content.hash.ContentFingerprint;
import ru.posts.content.hash.SimHasher;
import ru.posts.crosspost.config.CrosspostProperties;
import ru.posts.persistence.jdbc.RemotePostRegistryJdbc;

@ExtendWith(MockitoExtension.class)
class LoopGuardAndGraphTest {

    @Mock
    RemotePostRegistryJdbc registry;

    LoopGuard guard;
    CrosspostProperties properties;

    @BeforeEach
    void setUp() {
        properties = new CrosspostProperties();
        guard = new LoopGuard(registry, properties);
    }

    @Test
    void l1SkipsOwnPublication() {
        UUID channel = UUID.randomUUID();
        when(registry.findOwn(channel, "10"))
                .thenReturn(
                        Optional.of(new RemotePostRegistryJdbc.RegistryEntry("publication", (short) 0, "fp", "{}")));
        var d = guard.checkOwnRegistry(channel, "10", Map.of());
        assertThat(d.block()).isTrue();
        assertThat(d.status()).isEqualTo("skipped_own");
        assertThat(d.layer()).isEqualTo(LoopGuard.Layer.L1_OWN);
    }

    @Test
    void l1AllowsSuggestWhenEnabled() {
        UUID channel = UUID.randomUUID();
        when(registry.findOwn(channel, "10"))
                .thenReturn(Optional.of(new RemotePostRegistryJdbc.RegistryEntry("suggest", (short) 0, "fp", "{}")));
        var d = guard.checkOwnRegistry(channel, "10", Map.of("include_suggest_published", true));
        assertThat(d.block()).isFalse();
    }

    @Test
    void l1SkipsSuggestByDefault() {
        UUID channel = UUID.randomUUID();
        when(registry.findOwn(channel, "10"))
                .thenReturn(Optional.of(new RemotePostRegistryJdbc.RegistryEntry("suggest", (short) 0, "fp", "{}")));
        var d = guard.checkOwnRegistry(channel, "10", Map.of());
        assertThat(d.block()).isTrue();
        assertThat(d.reason()).isEqualTo("own_suggest_excluded");
    }

    @Test
    void l1AllowsPublicationWhenIncludePostsViaPosts() {
        UUID channel = UUID.randomUUID();
        when(registry.findOwn(channel, "10"))
                .thenReturn(
                        Optional.of(new RemotePostRegistryJdbc.RegistryEntry("publication", (short) 0, "fp", "{}")));
        var d = guard.checkOwnRegistry(channel, "10", Map.of("include_posts_published_via_posts", true));
        assertThat(d.block()).isFalse();
    }

    @Test
    void l2BlocksIntentFingerprint() {
        UUID channel = UUID.randomUUID();
        when(registry.isOwnByFingerprint(eq(channel), eq("fp"), any(Instant.class)))
                .thenReturn(false);
        when(registry.isIntentOwn(channel, "fp")).thenReturn(true);
        var d = guard.checkIntentRace(channel, "fp");
        assertThat(d.block()).isTrue();
        assertThat(d.layer()).isEqualTo(LoopGuard.Layer.L2_INTENT);
    }

    @Test
    void l3BlocksVisitedTarget() {
        UUID source = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        Map<String, Object> ref = Map.of(
                "root_origin",
                Map.of("channel_id", source.toString()),
                "visited_channel_ids",
                List.of(source.toString(), target.toString()),
                "hop",
                0);
        var d = guard.checkLineage(target, source, ref, (short) 0);
        assertThat(d.block()).isTrue();
        assertThat(d.status()).isEqualTo("skipped_loop");
    }

    @Test
    void l3BlocksHopCopiesAsSource() {
        UUID source = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        var d = guard.checkLineage(target, source, Map.of(), (short) 1);
        assertThat(d.block()).isTrue();
        assertThat(d.reason()).isEqualTo("hop_copy_not_source");
    }

    @Test
    void l4ExactFingerprintDuplicate() {
        UUID target = UUID.randomUUID();
        String fp = ContentFingerprint.fingerprint("same text", List.of());
        when(registry.listRecentFingerprints(eq(target), any(Instant.class), eq(200)))
                .thenReturn(List.of(fp));
        var d = guard.checkTargetDedup(target, fp, SimHasher.fingerprint("same text"));
        assertThat(d.block()).isTrue();
        assertThat(d.status()).isEqualTo("skipped_duplicate");
    }

    @Test
    void l4SameTextDifferentMediaIsExactNotAMiss() {
        UUID target = UUID.randomUUID();
        String stored = ContentFingerprint.fingerprint("same text", List.of("media-a"));
        String incoming = ContentFingerprint.fingerprint("same text", List.of("media-b"));
        assertThat(stored).isNotEqualTo(incoming);
        assertThat(ContentFingerprint.simKey(stored)).isEqualTo(ContentFingerprint.simKey(incoming));
        when(registry.listRecentFingerprints(eq(target), any(Instant.class), eq(200)))
                .thenReturn(List.of(stored));
        var d = guard.checkTargetDedup(target, incoming, 0L);
        assertThat(d.block()).isTrue();
        assertThat(d.reason()).isEqualTo("exact_fingerprint");
    }

    @Test
    void l7MaxBotIdentity() {
        var d = guard.checkBotIdentity("max", "bot-1", "bot-1");
        assertThat(d.block()).isTrue();
        assertThat(d.layer()).isEqualTo(LoopGuard.Layer.L7_BOT);
        assertThat(guard.checkBotIdentity("tg", "bot-1", "bot-1").block()).isFalse();
    }

    @Test
    void l5DetectsCycleAb() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID rule1 = UUID.randomUUID();
        UUID rule2 = UUID.randomUUID();
        var warnings = RuleGraphValidator.validate(
                List.of(new RuleGraphValidator.Edge(rule1, a, b), new RuleGraphValidator.Edge(rule2, b, a)));
        assertThat(warnings).isNotEmpty();
        assertThat(warnings.getFirst().code()).isEqualTo("cycle_detected");
    }

    @Test
    void l5DetectsTriangle() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        var warnings = RuleGraphValidator.validate(List.of(
                new RuleGraphValidator.Edge(UUID.randomUUID(), a, b),
                new RuleGraphValidator.Edge(UUID.randomUUID(), b, c),
                new RuleGraphValidator.Edge(UUID.randomUUID(), c, a)));
        assertThat(warnings).anyMatch(w -> "cycle_detected".equals(w.code()));
    }

    @Test
    void l5NoCycleOk() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        var warnings = RuleGraphValidator.validate(List.of(
                new RuleGraphValidator.Edge(UUID.randomUUID(), a, b),
                new RuleGraphValidator.Edge(UUID.randomUUID(), a, c)));
        assertThat(warnings).isEmpty();
    }

    @Test
    void idempotencyKeyFormat() {
        UUID rule = UUID.randomUUID();
        UUID src = UUID.randomUUID();
        UUID tgt = UUID.randomUUID();
        String key = ru.posts.crosspost.publish.CrosspostPostFactory.idempotencyKey(rule, src, "99", tgt);
        assertThat(key).isEqualTo("xp:" + rule + ":" + src + ":99:" + tgt);
    }

    @Test
    void buildSourceRefIncrementsHop() {
        UUID rule = UUID.randomUUID();
        UUID item = UUID.randomUUID();
        UUID channel = UUID.randomUUID();
        Map<String, Object> ref = LoopGuard.buildSourceRef(rule, item, "vk", channel, "1", Map.of());
        assertThat(ref.get("hop")).isEqualTo(1);
        assertThat(ref.get("visited_channel_ids")).asList().contains(channel.toString());
        Map<String, Object> next = LoopGuard.buildSourceRef(rule, item, "tg", UUID.randomUUID(), "2", ref);
        assertThat(next.get("hop")).isEqualTo(2);
    }
}
