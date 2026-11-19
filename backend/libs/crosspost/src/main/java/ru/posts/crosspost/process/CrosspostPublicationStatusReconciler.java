package ru.posts.crosspost.process;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.crosspost.metrics.CrosspostMetrics;
import ru.posts.persistence.entity.CrosspostItemEntity;
import ru.posts.persistence.entity.PublicationEntity;
import ru.posts.persistence.repository.CrosspostItemRepository;
import ru.posts.persistence.repository.PublicationRepository;
import org.springframework.context.annotation.Profile;

/** Polls queued crosspost items and mirrors publication terminal states. */
@Profile("!openapi")
@Component
public class CrosspostPublicationStatusReconciler {

    private final CrosspostItemRepository itemRepository;
    private final PublicationRepository publicationRepository;
    private final CrosspostMetrics metrics;

    public CrosspostPublicationStatusReconciler(
            CrosspostItemRepository itemRepository,
            PublicationRepository publicationRepository,
            CrosspostMetrics metrics) {
        this.itemRepository = itemRepository;
        this.publicationRepository = publicationRepository;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${posts.crosspost.status-poll-ms:5000}")
    @Transactional
    public void reconcile() {
        // Reuse due query pattern via status filter — load recent queued items per rule is heavy;
        // scan by finding items still in queued/partially_published with publications.
        List<CrosspostItemEntity> queued = itemRepository.findByStatusInOrderByUpdatedAtAsc(
                List.of("queued", "partially_published"), PageRequest.of(0, 100));
        for (CrosspostItemEntity item : queued) {
            refresh(item);
        }
    }

    public void refresh(CrosspostItemEntity item) {
        java.util.UUID[] ids = item.getPublicationIds();
        if (ids == null || ids.length == 0) {
            return;
        }
        int ok = 0;
        int fail = 0;
        int pending = 0;
        for (java.util.UUID id : ids) {
            PublicationEntity p = publicationRepository.findFirstByPublicationId(id).orElse(null);
            if (p == null) {
                pending++;
                continue;
            }
            String state = p.getState();
            if ("succeeded".equals(state)) {
                ok++;
            } else if (state != null && state.startsWith("failed")) {
                fail++;
            } else {
                pending++;
            }
        }
        String status;
        if (ok > 0 && fail == 0 && pending == 0) {
            status = "published";
        } else if (ok > 0 && fail > 0 && pending == 0) {
            status = "partially_published";
        } else if (fail > 0 && ok == 0 && pending == 0) {
            status = "failed";
        } else if (ok > 0 && pending > 0) {
            status = "partially_published";
        } else {
            return;
        }
        if (!status.equals(item.getStatus())) {
            item.setStatus(status);
            item.setUpdatedAt(Instant.now());
            itemRepository.save(item);
            metrics.itemStatus(status, "reconcile");
        }
    }

}
