package ru.posts.suggest.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.persistence.entity.SuggestItemEntity;
import ru.posts.persistence.repository.SuggestItemRepository;
import ru.posts.suggest.metrics.SuggestMetrics;

/**
 * Reconciles {@code suggest_items} against {@code social_remote_actions} outcomes (BE-34).
 *
 * <p>Looks up by idempotency key first, then by {@code remote_action_id} with {@code scheduled_for}
 * as a run_at partition hint.
 */
@Profile("!openapi")
@Service
public class SuggestRemoteActionReconciler {

    private static final Logger log = LoggerFactory.getLogger(SuggestRemoteActionReconciler.class);

    private final SuggestItemRepository itemRepository;
    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final SuggestMetrics metrics;

    public SuggestRemoteActionReconciler(
            SuggestItemRepository itemRepository,
            JdbcClient jdbcClient,
            ObjectMapper objectMapper,
            SuggestMetrics metrics) {
        this.itemRepository = itemRepository;
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    @Transactional
    public int reconcile(int limit) {
        int pageSize = Math.max(1, Math.min(limit, 200));
        List<SuggestItemEntity> items = itemRepository.findDueForRemoteReconcile(pageSize);
        int updated = 0;
        for (SuggestItemEntity item : items) {
            try {
                if (reconcileOne(item)) {
                    updated++;
                }
            } catch (RuntimeException ex) {
                log.warn(
                        "suggest remote reconcile failed itemId={} actionId={}: {}",
                        item.getId(),
                        item.getRemoteActionId(),
                        ex.getMessage());
            }
        }
        return updated;
    }

    private boolean reconcileOne(SuggestItemEntity item) {
        RemoteActionRow row = findAction(item);
        if (row == null) {
            return false;
        }
        Instant now = Instant.now();
        String state = row.state() == null ? "" : row.state();
        if ("succeeded".equals(state)) {
            if ("delete_post".equals(row.kind())
                    || "rejected".equals(item.getStatus())
                    || "filtered_out".equals(item.getStatus())) {
                if (!"deleted_remote".equals(item.getStatus())) {
                    item.setStatus("deleted_remote");
                    item.setUpdatedAt(now);
                    itemRepository.save(item);
                    metrics.recordStatus("deleted_remote");
                    return true;
                }
                return false;
            }
            String remoteId = extractRemoteId(row.resultJson());
            item.setStatus("published");
            if (remoteId != null && !remoteId.isBlank()) {
                item.setPublishedRemotePostId(remoteId);
            }
            item.setUpdatedAt(now);
            itemRepository.save(item);
            metrics.recordStatus("published");
            return true;
        }
        if ("failed_permanent".equals(state) || "cancelled".equals(state)) {
            item.setStatus("failed");
            item.setUpdatedAt(now);
            itemRepository.save(item);
            metrics.recordStatus("failed");
            return true;
        }
        if (("queued".equals(state) || "in_progress".equals(state))
                && ("scheduled".equals(item.getStatus()) || "approved".equals(item.getStatus()))) {
            item.setStatus("publishing");
            item.setUpdatedAt(now);
            itemRepository.save(item);
            return true;
        }
        return false;
    }

    private RemoteActionRow findAction(SuggestItemEntity item) {
        String publishKey = "suggest:" + item.getChannelId() + ":" + item.getVkPostId();
        RemoteActionRow row = findByIdempotency(publishKey);
        if (row == null) {
            row = findByIdempotency("suggest-delete:" + item.getChannelId() + ":" + item.getVkPostId());
        }
        if (row != null) {
            return row;
        }
        if (item.getRemoteActionId() == null) {
            return null;
        }
        return findById(item.getRemoteActionId(), item.getScheduledFor());
    }

    private RemoteActionRow findByIdempotency(String key) {
        return jdbcClient
                .sql(
                        """
                        SELECT id, kind, state, result::text AS result_json, run_at
                        FROM social_remote_actions
                        WHERE idempotency_key = :key
                        ORDER BY created_at DESC
                        LIMIT 1
                        """)
                .param("key", key)
                .query((rs, rowNum) -> new RemoteActionRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("kind"),
                        rs.getString("state"),
                        rs.getString("result_json"),
                        ts(rs.getTimestamp("run_at"))))
                .optional()
                .orElse(null);
    }

    private RemoteActionRow findById(UUID id, Instant scheduledFor) {
        if (scheduledFor != null) {
            Instant from = scheduledFor.minusSeconds(86_400);
            Instant to = scheduledFor.plusSeconds(7L * 86_400);
            var hit = jdbcClient
                    .sql(
                            """
                            SELECT id, kind, state, result::text AS result_json, run_at
                            FROM social_remote_actions
                            WHERE id = :id
                              AND run_at >= :fromTs
                              AND run_at < :toTs
                            LIMIT 1
                            """)
                    .param("id", id)
                    .param("fromTs", Timestamp.from(from))
                    .param("toTs", Timestamp.from(to))
                    .query((rs, rowNum) -> new RemoteActionRow(
                            rs.getObject("id", UUID.class),
                            rs.getString("kind"),
                            rs.getString("state"),
                            rs.getString("result_json"),
                            ts(rs.getTimestamp("run_at"))))
                    .optional();
            if (hit.isPresent()) {
                return hit.get();
            }
        }
        return jdbcClient
                .sql(
                        """
                        SELECT id, kind, state, result::text AS result_json, run_at
                        FROM social_remote_actions
                        WHERE id = :id
                        ORDER BY created_at DESC
                        LIMIT 1
                        """)
                .param("id", id)
                .query((rs, rowNum) -> new RemoteActionRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("kind"),
                        rs.getString("state"),
                        rs.getString("result_json"),
                        ts(rs.getTimestamp("run_at"))))
                .optional()
                .orElse(null);
    }

    private String extractRemoteId(String resultJson) {
        if (resultJson == null || resultJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(resultJson);
            String remoteId = node.path("remoteId").asText(null);
            if (remoteId != null && !remoteId.isBlank()) {
                return remoteId;
            }
            return node.path("remote_post_id").asText(null);
        } catch (Exception ex) {
            return null;
        }
    }

    private static Instant ts(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record RemoteActionRow(UUID id, String kind, String state, String resultJson, Instant runAt) {}
}
