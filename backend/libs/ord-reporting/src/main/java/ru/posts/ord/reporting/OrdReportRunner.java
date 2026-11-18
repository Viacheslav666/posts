package ru.posts.ord.reporting;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.common.uuid.UuidV7;
import ru.posts.ord.api.OrdApiException;
import ru.posts.ord.api.OrdCredentials;
import ru.posts.ord.api.OrdErrorClass;
import ru.posts.ord.api.OrdInvoiceRequest;
import ru.posts.ord.api.OrdProvider;
import ru.posts.ord.api.OrdProviderId;
import ru.posts.ord.api.OrdStatisticsItem;
import ru.posts.ord.api.OrdStatisticsRequest;
import ru.posts.ord.reporting.config.OrdReportingProperties;
import ru.posts.ord.reporting.impressions.OrdImpressionsFetcher;
import ru.posts.persistence.entity.OrdConnectionEntity;
import ru.posts.persistence.entity.OrdContractEntity;
import ru.posts.persistence.entity.OrdCreativeEntity;
import ru.posts.persistence.entity.OrdInvoiceEntity;
import ru.posts.persistence.entity.OrdPlatformEntity;
import ru.posts.persistence.entity.OrdReportRunEntity;
import ru.posts.persistence.entity.OrdStatisticsEntity;
import ru.posts.persistence.entity.OutboxEntity;
import ru.posts.persistence.entity.PublicationEntity;
import ru.posts.persistence.repository.OrdConnectionRepository;
import ru.posts.persistence.repository.OrdContractRepository;
import ru.posts.persistence.repository.OrdCreativeRepository;
import ru.posts.persistence.repository.OrdInvoiceRepository;
import ru.posts.persistence.repository.OrdReportRunRepository;
import ru.posts.persistence.repository.OrdStatisticsRepository;
import ru.posts.persistence.repository.OutboxRepository;
import ru.posts.persistence.repository.PublicationRepository;

/**
 * Shared ORD reporting orchestrator used by API {@code runReport} and the monthly scheduler job.
 *
 * <p>Idempotent per {@code (workspace, provider, year, month)} via {@code ord_report_runs} unique
 * key and invoice/statistics unique constraints. Re-runs that already {@code succeeded} return the
 * existing counts without re-submitting.
 */
@Profile("!openapi")
@Service
public class OrdReportRunner {

    private static final Logger log = LoggerFactory.getLogger(OrdReportRunner.class);
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    private final OrdReportingProperties properties;
    private final OrdConnectionRepository connectionRepository;
    private final OrdCreativeRepository creativeRepository;
    private final OrdContractRepository contractRepository;
    private final OrdInvoiceRepository invoiceRepository;
    private final OrdStatisticsRepository statisticsRepository;
    private final OrdReportRunRepository reportRunRepository;
    private final PublicationRepository publicationRepository;
    private final OutboxRepository outboxRepository;
    private final OrdApiKeyResolver apiKeyResolver;
    private final OrdImpressionsFetcher impressionsFetcher;
    private final OrdPlatformService platformService;
    private final List<OrdProvider> providers;
    private final OrdReportMetrics metrics;

    public OrdReportRunner(
            OrdReportingProperties properties,
            OrdConnectionRepository connectionRepository,
            OrdCreativeRepository creativeRepository,
            OrdContractRepository contractRepository,
            OrdInvoiceRepository invoiceRepository,
            OrdStatisticsRepository statisticsRepository,
            OrdReportRunRepository reportRunRepository,
            PublicationRepository publicationRepository,
            OutboxRepository outboxRepository,
            OrdApiKeyResolver apiKeyResolver,
            OrdImpressionsFetcher impressionsFetcher,
            OrdPlatformService platformService,
            List<OrdProvider> providers,
            OrdReportMetrics metrics) {
        this.properties = properties;
        this.connectionRepository = connectionRepository;
        this.creativeRepository = creativeRepository;
        this.contractRepository = contractRepository;
        this.invoiceRepository = invoiceRepository;
        this.statisticsRepository = statisticsRepository;
        this.reportRunRepository = reportRunRepository;
        this.publicationRepository = publicationRepository;
        this.outboxRepository = outboxRepository;
        this.apiKeyResolver = apiKeyResolver;
        this.impressionsFetcher = impressionsFetcher;
        this.platformService = platformService;
        this.providers = providers;
        this.metrics = metrics;
    }

    @Transactional
    public OrdReportResult run(UUID workspaceId, String providerCode, int year, int month) {
        long started = System.currentTimeMillis();
        OrdConnectionEntity connection =
                connectionRepository.findByWorkspaceIdOrderByUpdatedAtDesc(workspaceId).stream()
                        .filter(c -> "active".equals(c.getStatus()))
                        .filter(c -> providerCode == null || providerCode.equals(c.getProvider()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No active ORD connection"));
        String provider = connection.getProvider();

        Optional<OrdReportRunEntity> existing =
                reportRunRepository.findByWorkspaceIdAndProviderAndPeriodYearAndPeriodMonth(
                        workspaceId, provider, year, month);
        if (existing.isPresent() && "succeeded".equals(existing.get().getStatus())) {
            OrdReportRunEntity run = existing.get();
            return new OrdReportResult(
                    run.getId(),
                    run.getStatus(),
                    run.getInvoicesCount(),
                    run.getStatisticsCount(),
                    run.getPeriodYear(),
                    run.getPeriodMonth(),
                    null);
        }

        OrdReportRunEntity run = existing.orElseGet(OrdReportRunEntity::new);
        if (run.getId() == null) {
            run.setId(UuidV7.generate());
            run.setWorkspaceId(workspaceId);
            run.setProvider(provider);
            run.setPeriodYear(year);
            run.setPeriodMonth(month);
            run.setStartedAt(Instant.now());
        }
        run.setStatus("running");
        run.setAttempts(run.getAttempts() + 1);
        run.setNextRetryAt(null);
        run.setLastError(null);
        reportRunRepository.save(run);

        try {
            OrdProvider ordProvider = requireProvider(provider);
            String apiKey = apiKeyResolver.resolve(workspaceId, connection.getApiKeyEnc());
            if (apiKey == null || apiKey.isBlank()) {
                throw new OrdApiException(OrdErrorClass.AUTH, "ORD API key missing");
            }
            OrdCredentials credentials = new OrdCredentials(apiKey);

            YearMonth period = YearMonth.of(year, month);
            Instant periodStart = period.atDay(1).atStartOfDay(MOSCOW).toInstant();
            Instant periodEnd =
                    period.plusMonths(1).atDay(1).atStartOfDay(MOSCOW).toInstant();

            List<OrdCreativeEntity> creatives =
                    creativeRepository.findVisibleInPeriod(workspaceId, provider, periodStart, periodEnd);
            Map<UUID, OrdContractEntity> contractsById = new HashMap<>();
            Map<String, OrdStatisticsEntity> existingStatsByKey = indexStatistics(
                    statisticsRepository.findByWorkspaceIdAndPeriodYearAndPeriodMonth(workspaceId, year, month));
            List<OrdStatisticsItem> statsItems = new ArrayList<>(creatives.size());
            List<OrdStatisticsEntity> statsToPersist = new ArrayList<>(creatives.size());
            List<OrdCreativeEntity> creativesToPersist = new ArrayList<>();
            int statisticsCount = 0;

            // Prefetch publications for creatives that have posts (avoids N+1).
            List<UUID> postIds = creatives.stream()
                    .map(OrdCreativeEntity::getPostId)
                    .filter(id -> id != null)
                    .distinct()
                    .toList();
            Map<UUID, List<PublicationEntity>> pubsByPostId = new HashMap<>();
            for (UUID postId : postIds) {
                pubsByPostId.put(
                        postId,
                        publicationRepository.findByPostId(postId).stream()
                                .filter(p -> "succeeded".equals(p.getState())
                                        || "deleted".equals(p.getState())
                                        || p.getRemotePostId() != null)
                                .toList());
            }

            Instant now = Instant.now();
            for (OrdCreativeEntity creative : creatives) {
                boolean creativeDirty = false;
                List<PublicationEntity> publications = creative.getPostId() == null
                        ? List.of()
                        : pubsByPostId.getOrDefault(creative.getPostId(), List.of());
                if (creative.getDisplayStartedAt() == null) {
                    publications.stream()
                            .map(PublicationEntity::getPublishedAt)
                            .filter(ts -> ts != null)
                            .min(Instant::compareTo)
                            .ifPresent(creative::setDisplayStartedAt);
                    if (creative.getDisplayStartedAt() == null && creative.getRegisteredAt() != null) {
                        creative.setDisplayStartedAt(creative.getRegisteredAt());
                    }
                    if (creative.getDisplayStartedAt() != null) {
                        creativeDirty = true;
                    }
                }

                OrdPlatformEntity platform = null;
                PublicationEntity primaryPub = publications.isEmpty() ? null : publications.getFirst();
                if (primaryPub != null) {
                    platform = platformService.ensurePlatform(connection, ordProvider, primaryPub.getChannelId());
                }

                long imps = 0;
                String viewsSource = "unavailable";
                if (properties.getImpressionsOverride() != null && publications.isEmpty()) {
                    imps = properties.getImpressionsOverride();
                    viewsSource = "override";
                } else {
                    for (PublicationEntity pub : publications) {
                        boolean story = isStory(pub);
                        OrdImpressionsFetcher.Impression impression = impressionsFetcher.fetch(pub, story);
                        imps += impression.count();
                        if (!"unavailable".equals(impression.source())) {
                            viewsSource = impression.source();
                        }
                    }
                }

                OrdStatisticsEntity stats = upsertStatisticsInMemory(
                        existingStatsByKey,
                        workspaceId,
                        provider,
                        creative,
                        platform,
                        year,
                        month,
                        imps,
                        viewsSource,
                        now);
                if (!"submitted".equals(stats.getStatus())) {
                    statsToPersist.add(stats);
                }
                if ("submitted".equals(stats.getStatus()) || "draft".equals(stats.getStatus())) {
                    statisticsCount++;
                }

                LocalDate startActual = toMoscowDate(
                        creative.getDisplayStartedAt() == null
                                ? periodStart
                                : max(creative.getDisplayStartedAt(), periodStart));
                LocalDate endActual;
                if (creative.getDisplayEndedAt() != null
                        && !creative.getDisplayEndedAt().isAfter(periodEnd)
                        && !creative.getDisplayEndedAt().isBefore(periodStart)) {
                    endActual = toMoscowDate(creative.getDisplayEndedAt());
                } else {
                    endActual = period.atEndOfMonth();
                }

                BigDecimal amount = null;
                String contractExternalId = null;
                if (creative.getContractId() != null) {
                    OrdContractEntity contract = contractsById.computeIfAbsent(
                            creative.getContractId(),
                            id -> contractRepository.findById(id).orElse(null));
                    if (contract != null) {
                        contractExternalId = contract.getExternalId();
                        amount = contract.getAmount();
                    }
                }

                statsItems.add(new OrdStatisticsItem(
                        creative.getExternalId(),
                        platform == null ? null : platform.getExternalId(),
                        imps,
                        startActual,
                        endActual,
                        startActual,
                        endActual,
                        amount,
                        "other",
                        null,
                        contractExternalId));

                if (creative.isFinalStatsPending()
                        && creative.getDisplayEndedAt() != null
                        && !creative.getDisplayEndedAt().isAfter(periodEnd)) {
                    creative.setFinalStatsPending(false);
                    creativeDirty = true;
                }
                if (creativeDirty) {
                    creative.setUpdatedAt(now);
                    creativesToPersist.add(creative);
                }
            }

            if (!creativesToPersist.isEmpty()) {
                creativeRepository.saveAll(creativesToPersist);
            }

            int invoicesCount = 0;
            Set<UUID> creativeContractIds = creatives.stream()
                    .map(OrdCreativeEntity::getContractId)
                    .filter(id -> id != null)
                    .collect(Collectors.toSet());
            List<OrdContractEntity> contracts =
                    contractRepository.findByWorkspaceIdOrderByUpdatedAtDesc(workspaceId).stream()
                            .filter(c -> provider.equals(c.getProvider()))
                            .filter(c -> !c.isSelfPromo())
                            .filter(c -> creativeContractIds.contains(c.getId()))
                            .toList();
            for (OrdContractEntity contract : contracts) {
                OrdInvoiceEntity invoice = upsertInvoice(
                        workspaceId, connection, provider, year, month, contract, credentials, ordProvider);
                if ("submitted".equals(invoice.getStatus()) || "draft".equals(invoice.getStatus())) {
                    invoicesCount++;
                }
            }

            if (!statsItems.isEmpty()) {
                ordProvider.submitStatistics(credentials, new OrdStatisticsRequest(statsItems));
                Instant submittedAt = Instant.now();
                for (OrdStatisticsEntity row : statsToPersist) {
                    row.setStatus("submitted");
                    row.setSubmittedAt(submittedAt);
                    row.setUpdatedAt(submittedAt);
                }
            }
            if (!statsToPersist.isEmpty()) {
                // Single persist after ORD submit (draft→submitted) — avoids double 10k write.
                // Chunked to keep JDBC/Hibernate peak memory bounded on large workspaces.
                saveStatisticsInChunks(statsToPersist);
            }

            run.setStatus("succeeded");
            run.setInvoicesCount(invoicesCount);
            run.setStatisticsCount(statisticsCount);
            run.setFinishedAt(Instant.now());
            run.setLastError(null);
            reportRunRepository.save(run);

            long duration = System.currentTimeMillis() - started;
            metrics.recordRun(provider, "succeeded", duration, invoicesCount, statisticsCount);
            log.info(
                    "ORD report succeeded workspace={} provider={} period={}-{} invoices={} stats={} durationMs={}",
                    workspaceId,
                    provider,
                    year,
                    month,
                    invoicesCount,
                    statisticsCount,
                    duration);
            return new OrdReportResult(run.getId(), run.getStatus(), invoicesCount, statisticsCount, year, month, null);
        } catch (RuntimeException ex) {
            String message = safeMessage(ex);
            run.setStatus("error");
            run.setLastError(message);
            run.setFinishedAt(Instant.now());
            if (run.getAttempts() < properties.getMaxAttempts()) {
                run.setNextRetryAt(Instant.now().plus(properties.getRetryBackoff()));
            }
            reportRunRepository.save(run);
            metrics.recordError(
                    provider,
                    ex instanceof OrdApiException oae ? oae.errorClass().name() : "error");
            metrics.recordRun(provider, "error", System.currentTimeMillis() - started, 0, 0);
            writeOutbox(
                    workspaceId,
                    run.getId(),
                    "ord.problem",
                    Map.of(
                            "workspaceId", workspaceId.toString(),
                            "provider", provider,
                            "year", year,
                            "month", month,
                            "reason", "report_failed",
                            "error", message));
            throw ex;
        }
    }

    /**
     * In-memory upsert keyed by {@code (creativeId, platformId)}. Existing rows are prefetched once
     * per run to avoid N+1 queries and the O(n²) null-platform fallback that Spring Data {@code
     * ...AndPlatformId(null)} cannot match.
     */
    private OrdStatisticsEntity upsertStatisticsInMemory(
            Map<String, OrdStatisticsEntity> existingByKey,
            UUID workspaceId,
            String provider,
            OrdCreativeEntity creative,
            OrdPlatformEntity platform,
            int year,
            int month,
            long imps,
            String viewsSource,
            Instant now) {
        UUID platformId = platform == null ? null : platform.getId();
        String key = statisticsKey(creative.getId(), platformId);
        OrdStatisticsEntity stats = existingByKey.get(key);
        if (stats == null) {
            stats = new OrdStatisticsEntity();
            stats.setId(UuidV7.generate());
            stats.setWorkspaceId(workspaceId);
            stats.setCreativeId(creative.getId());
            stats.setPlatformId(platformId);
            stats.setProvider(provider);
            stats.setPeriodYear(year);
            stats.setPeriodMonth(month);
            stats.setCreatedAt(now);
            stats.setStatus("draft");
            existingByKey.put(key, stats);
        }
        if ("submitted".equals(stats.getStatus())) {
            return stats;
        }
        stats.setImpsFact(imps);
        stats.setViewsSource(viewsSource);
        stats.setUpdatedAt(now);
        return stats;
    }

    private void saveStatisticsInChunks(List<OrdStatisticsEntity> rows) {
        final int chunkSize = 500;
        for (int i = 0; i < rows.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, rows.size());
            statisticsRepository.saveAll(rows.subList(i, end));
        }
    }

    private static Map<String, OrdStatisticsEntity> indexStatistics(List<OrdStatisticsEntity> rows) {
        Map<String, OrdStatisticsEntity> map = new HashMap<>(Math.max(16, rows.size() * 2));
        for (OrdStatisticsEntity row : rows) {
            map.put(statisticsKey(row.getCreativeId(), row.getPlatformId()), row);
        }
        return map;
    }

    private static String statisticsKey(UUID creativeId, UUID platformId) {
        return creativeId + "|" + (platformId == null ? "_" : platformId);
    }

    private OrdInvoiceEntity upsertInvoice(
            UUID workspaceId,
            OrdConnectionEntity connection,
            String provider,
            int year,
            int month,
            OrdContractEntity contract,
            OrdCredentials credentials,
            OrdProvider ordProvider) {
        String externalId = "invoice:" + contract.getExternalId() + ":" + year + "-" + month;
        OrdInvoiceEntity invoice = invoiceRepository
                .findByWorkspaceIdAndProviderAndPeriodYearAndPeriodMonthAndExternalId(
                        workspaceId, provider, year, month, externalId)
                .orElseGet(OrdInvoiceEntity::new);
        Instant now = Instant.now();
        if (invoice.getId() == null) {
            invoice.setId(UuidV7.generate());
            invoice.setWorkspaceId(workspaceId);
            invoice.setConnectionId(connection.getId());
            invoice.setProvider(provider);
            invoice.setExternalId(externalId);
            invoice.setPeriodYear(year);
            invoice.setPeriodMonth(month);
            invoice.setContractId(contract.getId());
            invoice.setCreatedAt(now);
            invoice.setStatus("draft");
        }
        if (invoice.getDeletedAt() != null) {
            return invoice;
        }
        if ("submitted".equals(invoice.getStatus())) {
            return invoice;
        }
        invoice.setAmount(contract.getAmount());
        invoice.setUpdatedAt(now);
        invoiceRepository.save(invoice);

        YearMonth period = YearMonth.of(year, month);
        ordProvider.submitInvoice(
                credentials,
                new OrdInvoiceRequest(
                        externalId,
                        contract.getExternalId(),
                        period.atEndOfMonth(),
                        period.atDay(1),
                        period.atEndOfMonth(),
                        contract.getSerial(),
                        contract.getAmount(),
                        contract.getVatRate(),
                        "advertiser",
                        "publisher",
                        List.of()));
        invoice.setStatus("submitted");
        invoice.setSubmittedAt(now);
        invoice.setUpdatedAt(Instant.now());
        return invoiceRepository.save(invoice);
    }

    private OrdProvider requireProvider(String code) {
        OrdProviderId id = OrdProviderId.fromCode(code);
        return providers.stream()
                .filter(p -> p.providerId() == id)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("ORD provider not registered: " + code));
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

    private static boolean isStory(PublicationEntity publication) {
        // Stories share publications table; kind/meta may mark them. Fallback: remote id prefix.
        if (publication.getRemotePostId() != null
                && publication.getRemotePostId().startsWith("story:")) {
            return true;
        }
        return false;
    }

    private static LocalDate toMoscowDate(Instant instant) {
        return instant.atZone(MOSCOW).toLocalDate();
    }

    private static Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static String safeMessage(Throwable ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "ORD report error";
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("apikey") || lower.contains("api_key") || lower.contains("bearer")) {
            return "ORD report error";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
