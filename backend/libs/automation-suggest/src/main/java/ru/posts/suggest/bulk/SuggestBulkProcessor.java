package ru.posts.suggest.bulk;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.persistence.entity.SuggestBulkJobEntity;
import ru.posts.persistence.entity.SuggestItemEntity;
import ru.posts.persistence.entity.SuggestSettingsEntity;
import ru.posts.persistence.repository.SuggestBulkJobRepository;
import ru.posts.persistence.repository.SuggestItemRepository;
import ru.posts.persistence.repository.SuggestSettingsRepository;
import ru.posts.suggest.SuggestProcessingService;

/** Processes bulk approve/reject jobs; sync loop for IT simplicity (BE-34). */
@Service
public class SuggestBulkProcessor {

    private static final Logger log = LoggerFactory.getLogger(SuggestBulkProcessor.class);

    private final SuggestBulkJobRepository jobRepository;
    private final SuggestItemRepository itemRepository;
    private final SuggestSettingsRepository settingsRepository;
    private final SuggestProcessingService processingService;

    public SuggestBulkProcessor(
            SuggestBulkJobRepository jobRepository,
            SuggestItemRepository itemRepository,
            SuggestSettingsRepository settingsRepository,
            SuggestProcessingService processingService) {
        this.jobRepository = jobRepository;
        this.itemRepository = itemRepository;
        this.settingsRepository = settingsRepository;
        this.processingService = processingService;
    }

    @Transactional
    public SuggestBulkJobEntity process(UUID jobId) {
        SuggestBulkJobEntity job = jobRepository
                .findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Bulk job not found"));
        if ("succeeded".equals(job.getStatus()) || "failed".equals(job.getStatus())) {
            return job;
        }
        Instant now = Instant.now();
        job.setStatus("running");
        job.setUpdatedAt(now);
        jobRepository.save(job);

        SuggestSettingsEntity settings = settingsRepository
                .findById(job.getSettingsId())
                .orElseThrow(() -> new IllegalArgumentException("Settings not found"));

        List<SuggestItemEntity> items = resolveItems(job);
        job.setTotal(items.size());
        int succeeded = 0;
        int failed = 0;
        boolean approve = job.getAction() != null && job.getAction().startsWith("approve");
        boolean deleteRemote = job.getAction() != null && job.getAction().contains("reject");

        for (SuggestItemEntity item : items) {
            try {
                if (approve) {
                    if ("pending_moderation".equals(item.getStatus()) || "new".equals(item.getStatus())) {
                        processingService.approve(item, settings, job.getCreatedBy(), null, null, null);
                    }
                } else {
                    if ("pending_moderation".equals(item.getStatus())
                            || "new".equals(item.getStatus())
                            || "filtered_out".equals(item.getStatus())) {
                        processingService.reject(item, settings, job.getCreatedBy(), deleteRemote);
                    }
                }
                succeeded++;
            } catch (RuntimeException ex) {
                failed++;
                log.debug("bulk item {} failed: {}", item.getId(), ex.getMessage());
            }
            job.setProcessed(succeeded + failed);
            job.setSucceeded(succeeded);
            job.setFailed(failed);
            job.setUpdatedAt(Instant.now());
            jobRepository.save(job);
        }

        job.setStatus(failed > 0 && succeeded == 0 ? "failed" : "succeeded");
        job.setFinishedAt(Instant.now());
        job.setUpdatedAt(job.getFinishedAt());
        if (failed > 0 && succeeded == 0) {
            job.setErrorMessage("All items failed");
        }
        return jobRepository.save(job);
    }

    private List<SuggestItemEntity> resolveItems(SuggestBulkJobEntity job) {
        String action = job.getAction() == null ? "" : job.getAction();
        if ("approve_ids".equals(action) || "reject_ids".equals(action)) {
            List<SuggestItemEntity> out = new ArrayList<>();
            if (job.getItemIds() != null) {
                for (UUID id : job.getItemIds()) {
                    itemRepository.findById(id).ifPresent(out::add);
                }
            }
            return out;
        }
        if ("approve_all".equals(action)) {
            return itemRepository.findBySettingsIdAndStatusOrderByCreatedAtAsc(
                    job.getSettingsId(), "pending_moderation");
        }
        if ("reject_all".equals(action)) {
            return itemRepository.findBySettingsIdAndStatusOrderByCreatedAtAsc(
                    job.getSettingsId(), "pending_moderation");
        }
        return List.of();
    }
}
