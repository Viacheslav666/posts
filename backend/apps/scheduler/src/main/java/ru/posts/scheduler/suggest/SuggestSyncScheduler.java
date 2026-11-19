package ru.posts.scheduler.suggest;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.posts.suggest.sync.SuggestSyncService;

/** BE-34: periodic wall.get(filter=suggests) reconciliation for active suggest settings. */
@Component
@ConditionalOnBean(SuggestSyncService.class)
public class SuggestSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SuggestSyncScheduler.class);

    private final SuggestSyncService syncService;

    public SuggestSyncScheduler(SuggestSyncService syncService) {
        this.syncService = syncService;
    }

    @Scheduled(fixedDelayString = "${posts.scheduler.suggest-sync-interval-ms:180000}")
    @SchedulerLock(name = "suggestSyncDue", lockAtMostFor = "2m", lockAtLeastFor = "10s")
    public void syncDue() {
        int synced = syncService.syncDue(20);
        if (synced > 0) {
            log.info("suggest syncDue completed channels={}", synced);
        }
    }
}
