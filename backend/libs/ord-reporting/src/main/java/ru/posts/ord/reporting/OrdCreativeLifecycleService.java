package ru.posts.ord.reporting;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.ord.reporting.config.OrdReportingProperties;
import ru.posts.persistence.entity.OrdContractEntity;
import ru.posts.persistence.entity.OrdCreativeEntity;
import ru.posts.persistence.entity.OutboxEntity;
import ru.posts.persistence.repository.OrdContractRepository;
import ru.posts.persistence.repository.OrdCreativeRepository;
import ru.posts.persistence.repository.OutboxRepository;

/**
 * Creative display lifecycle: expiry warnings, display end on post takedown, keep erid for
 * reporting (ADR-0011).
 */
@Profile("!openapi")
@Service
public class OrdCreativeLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(OrdCreativeLifecycleService.class);
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    private final OrdCreativeRepository creativeRepository;
    private final OrdContractRepository contractRepository;
    private final OutboxRepository outboxRepository;
    private final OrdReportingProperties properties;
    private final OrdReportMetrics metrics;

    public OrdCreativeLifecycleService(
            OrdCreativeRepository creativeRepository,
            OrdContractRepository contractRepository,
            OutboxRepository outboxRepository,
            OrdReportingProperties properties,
            OrdReportMetrics metrics) {
        this.creativeRepository = creativeRepository;
        this.contractRepository = contractRepository;
        this.outboxRepository = outboxRepository;
        this.properties = properties;
        this.metrics = metrics;
    }

    /** Called after successful social delete / delete_story for an ad post. */
    @Transactional
    public void markDisplayEnded(UUID postId, Instant endedAt) {
        List<OrdCreativeEntity> creatives = creativeRepository.findByPostId(postId);
        Instant end = endedAt == null ? Instant.now() : endedAt;
        for (OrdCreativeEntity creative : creatives) {
            if (creative.getDisplayEndedAt() != null) {
                continue;
            }
            creative.setDisplayEndedAt(end);
            creative.setFinalStatsPending(true);
            creative.setUpdatedAt(Instant.now());
            creativeRepository.save(creative);
            writeOutbox(
                    creative.getWorkspaceId(),
                    creative.getId(),
                    "ord.display_ended",
                    Map.of(
                            "workspaceId", creative.getWorkspaceId().toString(),
                            "creativeId", creative.getId().toString(),
                            "postId", postId.toString(),
                            "erid", creative.getErid() == null ? "" : creative.getErid(),
                            "displayEndedAt", end.toString()));
            log.info("ORD creative display ended creativeId={} postId={}", creative.getId(), postId);
        }
    }

    @Transactional
    public int sendExpiryWarnings(Instant now) {
        int sent = 0;
        int warnDays = Math.max(0, properties.getExpiryWarningDays());
        for (OrdCreativeEntity creative : creativeRepository.findCandidatesForExpiryWarning()) {
            Instant start = creative.getDisplayStartedAt();
            if (start == null) {
                continue;
            }
            Instant displayEnd = start.plus(creative.getDisplayPeriodDays(), ChronoUnit.DAYS);
            Instant contractEnd = null;
            if (creative.getContractId() != null) {
                OrdContractEntity contract =
                        contractRepository.findById(creative.getContractId()).orElse(null);
                if (contract != null && contract.getDateEnd() != null) {
                    contractEnd = contract.getDateEnd().atStartOfDay(MOSCOW).toInstant();
                }
            }
            Instant effectiveEnd = displayEnd;
            if (contractEnd != null && contractEnd.isBefore(effectiveEnd)) {
                effectiveEnd = contractEnd;
            }
            long daysLeft = ChronoUnit.DAYS.between(
                    LocalDate.ofInstant(now, MOSCOW), LocalDate.ofInstant(effectiveEnd, MOSCOW));
            if (daysLeft < 0 || daysLeft > warnDays) {
                continue;
            }
            creative.setExpiryWarningSentAt(now);
            creative.setUpdatedAt(now);
            creativeRepository.save(creative);
            writeOutbox(
                    creative.getWorkspaceId(),
                    creative.getId(),
                    "ord.expiry_warning",
                    Map.of(
                            "workspaceId",
                            creative.getWorkspaceId().toString(),
                            "creativeId",
                            creative.getId().toString(),
                            "postId",
                            creative.getPostId() == null
                                    ? ""
                                    : creative.getPostId().toString(),
                            "erid",
                            creative.getErid() == null ? "" : creative.getErid(),
                            "daysLeft",
                            daysLeft,
                            "endsAt",
                            effectiveEnd.toString()));
            metrics.recordExpiryWarning();
            sent++;
        }
        return sent;
    }

    private void writeOutbox(UUID workspaceId, UUID aggregateId, String eventType, Map<String, Object> payload) {
        OutboxEntity event = new OutboxEntity();
        event.setAggregate("ord");
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        Map<String, Object> body = new LinkedHashMap<>(payload);
        body.putIfAbsent("workspaceId", workspaceId.toString());
        event.setPayload(body);
        event.setCreatedAt(Instant.now());
        outboxRepository.save(event);
    }
}
