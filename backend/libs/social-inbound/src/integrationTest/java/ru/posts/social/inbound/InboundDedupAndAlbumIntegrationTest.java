package ru.posts.social.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.posts.common.uuid.UuidV7;
import ru.posts.social.inbound.album.JdbcAlbumFragmentStore;
import ru.posts.social.inbound.album.TelegramAlbumAggregator;
import ru.posts.social.inbound.dedup.InboundEventStore;

@SpringBootTest(classes = SocialInboundIntegrationTestApp.class)
@Testcontainers
class InboundDedupAndAlbumIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("posts")
            .withUsername("posts")
            .withPassword("posts");

    @Autowired
    private InboundEventStore eventStore;

    @Autowired
    private JdbcAlbumFragmentStore albumStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void unprocessedDuplicateIsRedispatchedOnceAndAProcessedOneIsDropped() {
        UUID channelId = UuidV7.generate();
        Instant firstAt = Instant.now().minusSeconds(30);
        SocialInboundEvent first = event(channelId, "dedup-1", firstAt);
        InboundEventStore.DispatchClaim claim =
                eventStore.claimForDispatch(first).orElseThrow();
        InboundEventStore.DispatchClaim again = eventStore
                .claimForDispatch(event(channelId, "dedup-1", Instant.now()))
                .orElseThrow();
        assertThat(again.id()).isEqualTo(claim.id());
        assertThat(again.receivedAt()).isEqualTo(claim.receivedAt());

        eventStore.markProcessed(claim.id(), claim.receivedAt());
        assertThat(eventStore.claimForDispatch(event(channelId, "dedup-1", Instant.now())))
                .isEmpty();
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM social_inbound_events WHERE channel_id = ? AND dedup_key = ?",
                Integer.class,
                channelId,
                "dedup-1");
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void parallelDeliveriesOfTheSameEventInsertOneRow() throws Exception {
        UUID channelId = UuidV7.generate();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Optional<InboundEventStore.DispatchClaim>>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Instant received = Instant.now().plusMillis(i);
                futures.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return eventStore.claimForDispatch(event(channelId, "dedup-race", received));
                }));
            }
            start.countDown();
            UUID id = null;
            int claimed = 0;
            for (Future<Optional<InboundEventStore.DispatchClaim>> future : futures) {
                Optional<InboundEventStore.DispatchClaim> claim = future.get(30, TimeUnit.SECONDS);
                if (claim.isPresent()) {
                    claimed++;
                    id = id == null ? claim.get().id() : id;
                    assertThat(claim.get().id()).isEqualTo(id);
                }
            }
            assertThat(claimed).isPositive();
            Integer rows = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM social_inbound_events WHERE channel_id = ? AND dedup_key = ?",
                    Integer.class,
                    channelId,
                    "dedup-race");
            assertThat(rows).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void albumFragmentsFromTwoStoresFlushTogetherAfterSilence() {
        UUID channelId = UuidV7.generate();
        UUID botId = UuidV7.generate();
        Instant t0 = Instant.now().minusSeconds(10);
        albumStore.add(channelId.toString(), "mg-1", botId.toString(), update(1), t0);
        albumStore.add(channelId.toString(), "mg-1", botId.toString(), update(2), t0.plusSeconds(1));

        assertThat(albumStore.drainReady(t0.plusSeconds(2))).isEmpty();
        List<TelegramAlbumAggregator.ReadyAlbum> ready = albumStore.drainReady(t0.plusSeconds(5));
        assertThat(ready).hasSize(1);
        assertThat(ready.getFirst().fragments()).hasSize(2);
        assertThat(albumStore.drainReady(Instant.now())).isEmpty();
    }

    private static SocialInboundEvent event(UUID channelId, String dedupKey, Instant receivedAt) {
        return new SocialInboundEvent(
                "vk",
                channelId,
                UuidV7.generate(),
                SocialEventKind.COMMENT_NEW,
                dedupKey,
                java.util.Map.of(),
                null,
                "text",
                null,
                java.util.List.of(),
                receivedAt,
                receivedAt,
                "{}",
                java.util.List.of());
    }

    private static ObjectNode update(int id) {
        ObjectNode node = new ObjectMapper().createObjectNode();
        node.put("update_id", id);
        node.put("text", "part-" + id);
        return node;
    }
}
