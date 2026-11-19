package ru.posts.scheduler.suggest;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.posts.suggest.service.SuggestRemoteActionReconciler;

/** BE-34: reconcile suggest_items against social_remote_actions outcomes. */
@Component
@ConditionalOnBean(SuggestRemoteActionReconciler.class)
public class SuggestRemoteReconcileScheduler {

    private static final Logger log = LoggerFactory.getLogger(SuggestRemoteReconcileScheduler.class);

    private final SuggestRemoteActionReconciler reconciler;

    public SuggestRemoteReconcileScheduler(SuggestRemoteActionReconciler reconciler) {
        this.reconciler = reconciler;
    }

    @Scheduled(fixedDelayString = "${posts.scheduler.suggest-reconcile-interval-ms:30000}")
    @SchedulerLock(name = "suggestRemoteReconcile", lockAtMostFor = "1m", lockAtLeastFor = "5s")
    public void reconcile() {
        int updated = reconciler.reconcile(100);
        if (updated > 0) {
            log.info("suggest remote reconcile updated={}", updated);
        }
    }
}
