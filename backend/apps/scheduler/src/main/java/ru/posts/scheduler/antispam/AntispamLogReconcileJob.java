package ru.posts.scheduler.antispam;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.posts.antispam.service.AntispamLogReconciler;

@Component
@ConditionalOnBean(AntispamLogReconciler.class)
public class AntispamLogReconcileJob {

    private final AntispamLogReconciler reconciler;

    public AntispamLogReconcileJob(AntispamLogReconciler reconciler) {
        this.reconciler = reconciler;
    }

    @Scheduled(fixedDelayString = "${posts.antispam.reconcile-interval-ms:15000}")
    @SchedulerLock(name = "antispamLogReconcile", lockAtMostFor = "5m", lockAtLeastFor = "2s")
    public void run() {
        reconciler.reconcile(200);
    }
}
