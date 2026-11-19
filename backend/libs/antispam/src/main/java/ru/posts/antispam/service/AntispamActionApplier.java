package ru.posts.antispam.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.antispam.metrics.AntispamMetrics;
import ru.posts.antispam.model.AntispamAction;
import ru.posts.antispam.model.AntispamActionKind;
import ru.posts.antispam.model.AntispamSection;
import ru.posts.antispam.model.Verdict;
import ru.posts.common.uuid.UuidV7;
import ru.posts.persistence.jdbc.AntispamLogJdbc;
import ru.posts.persistence.jdbc.OutboxJdbc;
import ru.posts.persistence.service.RemoteActionService;
import ru.posts.social.inbound.SocialInboundEvent;

/** Enqueues remote actions and writes antispam_log (idempotent). */
public class AntispamActionApplier {

    private static final Logger log = LoggerFactory.getLogger(AntispamActionApplier.class);
    private static final int BURST_THRESHOLD = 60;

    private final RemoteActionService remoteActionService;
    private final AntispamLogJdbc logJdbc;
    private final OutboxJdbc outboxJdbc;
    private final ObjectMapper objectMapper;
    private final AntispamMetrics metrics;
    private final RemoteRefFactory remoteRefFactory;

    public AntispamActionApplier(
            RemoteActionService remoteActionService,
            AntispamLogJdbc logJdbc,
            OutboxJdbc outboxJdbc,
            ObjectMapper objectMapper,
            AntispamMetrics metrics,
            RemoteRefFactory remoteRefFactory) {
        this.remoteActionService = remoteActionService;
        this.logJdbc = logJdbc;
        this.outboxJdbc = outboxJdbc;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.remoteRefFactory = remoteRefFactory;
    }

    @Transactional
    public UUID apply(SocialInboundEvent event, AntispamSection section, Verdict verdict) {
        RemoteRefFactory.RemoteIds remote = remoteRefFactory.from(event, section);
        if (logJdbc.findIdByDedup(event.channelId(), section.wire(), remote.remoteCommentKey())
                .isPresent()) {
            metrics.recordAction("duplicate");
            return logJdbc.findIdByDedup(event.channelId(), section.wire(), remote.remoteCommentKey())
                    .orElseThrow();
        }

        Instant now = Instant.now();
        boolean restorable = "vk".equalsIgnoreCase(event.network());
        String logAction = mapLogAction(verdict);
        String state = verdict.dryRun() ? "done" : "pending";

        UUID logId = UuidV7.generate();
        List<UUID> actionIds = new ArrayList<>();

        if (!verdict.dryRun()) {
            maybeNotifyBurst(event, now);
            Instant runAt = now;
            // TG delete window: fail fast if message older than 48h
            if (isTg(event) && isOlderThan(event.occurredAt(), Duration.ofDays(2))) {
                state = "failed";
                logAction = mapLogAction(verdict);
                AntispamLogJdbc.AntispamLogRow failed = new AntispamLogJdbc.AntispamLogRow(
                        logId,
                        event.workspaceId(),
                        event.channelId(),
                        verdict.ruleId(),
                        section.wire(),
                        remote.remoteCommentKey(),
                        logJdbc.toJson(remote.remoteRef()),
                        remote.remoteUserId(),
                        event.text(),
                        summarizeAttachments(event),
                        verdict.reasons(),
                        logAction,
                        state,
                        false,
                        List.of(),
                        now,
                        null,
                        null);
                UUID inserted = logJdbc.insert(failed);
                metrics.recordAction("tg_delete_window_expired");
                return inserted;
            }

            for (AntispamAction action : verdict.actions()) {
                if (action.kind() == AntispamActionKind.FLAG) {
                    continue;
                }
                UUID actionId = enqueue(event, section, remote, verdict, action, runAt);
                actionIds.add(actionId);
                // reply before delete: same runAt; ban after delete slightly delayed to keep order under rate limits
                if (action.kind() == AntispamActionKind.DELETE) {
                    runAt = runAt.plusMillis(50);
                } else if (action.kind() == AntispamActionKind.BAN) {
                    runAt = runAt.plusMillis(100);
                } else if (action.kind() == AntispamActionKind.REPLY) {
                    runAt = runAt.plusMillis(10);
                }
            }
            state = actionIds.isEmpty() ? "done" : "pending";
        }

        AntispamLogJdbc.AntispamLogRow row = new AntispamLogJdbc.AntispamLogRow(
                logId,
                event.workspaceId(),
                event.channelId(),
                verdict.ruleId(),
                section.wire(),
                remote.remoteCommentKey(),
                logJdbc.toJson(remote.remoteRef()),
                remote.remoteUserId(),
                event.text(),
                summarizeAttachments(event),
                verdict.reasons(),
                logAction,
                state,
                restorable && !verdict.dryRun() && containsDelete(verdict),
                actionIds,
                now,
                null,
                null);
        UUID inserted = logJdbc.insert(row);
        if (event.receivedAt() != null) {
            metrics.recordEventToAction(Duration.between(event.receivedAt(), Instant.now()));
        }
        metrics.recordAction(verdict.dryRun() ? "flagged" : "enqueued");
        return inserted;
    }

    private UUID enqueue(
            SocialInboundEvent event,
            AntispamSection section,
            RemoteRefFactory.RemoteIds remote,
            Verdict verdict,
            AntispamAction action,
            Instant runAt) {
        String kind =
                switch (action.kind()) {
                    case REPLY -> "reply_comment";
                    case DELETE -> section == AntispamSection.WALL_POST || section == AntispamSection.SUGGESTED_POST
                            ? "delete_post"
                            : "delete_comment";
                    case BAN -> "ban_user";
                    case FLAG -> throw new IllegalStateException("FLAG is not a remote action");
                };
        String idempotencyKey = "antispam:"
                + event.channelId()
                + ":"
                + section.wire()
                + ":"
                + remote.remoteCommentKey()
                + ":"
                + action.kind().name().toLowerCase(Locale.ROOT);

        Map<String, Object> target = new LinkedHashMap<>();
        target.put("commentType", remote.commentType());
        if (remote.ownerId() != null) {
            target.put("ownerId", remote.ownerId());
        }
        if (remote.remotePostId() != null) {
            target.put("remotePostId", remote.remotePostId());
        }
        if (remote.remoteCommentId() != null) {
            target.put("remoteCommentId", remote.remoteCommentId());
        }
        if (remote.remoteUserId() != null) {
            target.put("remoteUserId", remote.remoteUserId());
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        if (action.kind() == AntispamActionKind.REPLY) {
            String template = String.valueOf(action.params().getOrDefault("template", ""));
            payload.put("text", renderReply(template, verdict.reasons()));
            payload.put("replyToCommentId", remote.remoteCommentId());
        } else if (action.kind() == AntispamActionKind.BAN) {
            int days = ((Number) action.params().getOrDefault("days", 7)).intValue();
            payload.put(
                    "banEndDate",
                    Instant.now().plus(Duration.ofDays(Math.max(1, days))).toString());
            payload.put("banReason", 1);
            payload.put("banComment", String.valueOf(action.params().getOrDefault("reason", "spam")));
            payload.put("banCommentVisible", Boolean.TRUE.equals(action.params().get("comment_visible")));
        }

        Map<String, Object> sourceRef = Map.of(
                "rule_id", verdict.ruleId() == null ? "" : verdict.ruleId().toString(),
                "section", section.wire(),
                "reasons", verdict.reasons());

        return remoteActionService.enqueue(
                kind,
                event.workspaceId(),
                event.channelId(),
                writeJson(target),
                writeJson(payload),
                idempotencyKey,
                runAt,
                "antispam",
                writeJson(sourceRef),
                null);
    }

    private void maybeNotifyBurst(SocialInboundEvent event, Instant now) {
        long count = logJdbc.countActionsSince(event.channelId(), now.minus(Duration.ofMinutes(1)));
        if (count + 1 < BURST_THRESHOLD) {
            return;
        }
        try {
            outboxJdbc.append(
                    "channel",
                    event.channelId(),
                    "antispam.spam_attack",
                    writeJson(Map.of(
                            "workspaceId", event.workspaceId().toString(),
                            "channelId", event.channelId().toString(),
                            "network", event.network(),
                            "actionsLastMinute", count + 1,
                            "message", "Spam attack detected: antispam actions exceeded threshold")));
        } catch (RuntimeException ex) {
            log.warn("Failed to enqueue spam attack notification channelId={}", event.channelId(), ex);
        }
    }

    private static boolean containsDelete(Verdict verdict) {
        return verdict.actions().stream().anyMatch(a -> a.kind() == AntispamActionKind.DELETE);
    }

    private static String mapLogAction(Verdict verdict) {
        if (verdict.dryRun()) {
            return "flagged";
        }
        boolean deleted = verdict.actions().stream().anyMatch(a -> a.kind() == AntispamActionKind.DELETE);
        boolean banned = verdict.actions().stream().anyMatch(a -> a.kind() == AntispamActionKind.BAN);
        boolean replied = verdict.actions().stream().anyMatch(a -> a.kind() == AntispamActionKind.REPLY);
        if (deleted && banned) {
            return "deleted_banned";
        }
        if (deleted) {
            return "deleted";
        }
        if (replied) {
            return "replied";
        }
        return "flagged";
    }

    private static String renderReply(String template, List<String> reasons) {
        String t = template == null || template.isBlank() ? "Ваше сообщение удалено: {reasons}" : template;
        String reasonText = reasons == null ? "" : reasons.stream().collect(Collectors.joining(", "));
        return t.replace("{reasons}", reasonText);
    }

    private static String summarizeAttachments(SocialInboundEvent event) {
        if (event.attachments() == null || event.attachments().isEmpty()) {
            return null;
        }
        return event.attachments().stream()
                .map(a -> a.type() == null ? "unknown" : a.type())
                .collect(Collectors.joining(","));
    }

    private static boolean isTg(SocialInboundEvent event) {
        String n = event.network();
        return n != null && (n.equalsIgnoreCase("tg") || n.equalsIgnoreCase("telegram"));
    }

    private static boolean isOlderThan(Instant occurredAt, Duration window) {
        if (occurredAt == null) {
            return false;
        }
        return occurredAt.isBefore(Instant.now().minus(window));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
