package ru.posts.scheduler.dispatch;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DispatchScheduler {

    private final PublicationDispatchService dispatchService;

    public DispatchScheduler(PublicationDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    /** Competitive claim — no ShedLock; SKIP LOCKED coordinates instances. */
    @Scheduled(fixedDelayString = "${posts.scheduler.dispatch-interval-ms:1000}")
    public void dispatch() {
        dispatchService.dispatchDueBatch();
    }

    @Scheduled(fixedDelayString = "${posts.scheduler.reaper-interval-ms:30000}")
    @SchedulerLock(name = "publicationReaper", lockAtMostFor = "25s", lockAtLeastFor = "5s")
    public void reaper() {
        dispatchService.reapStuck();
    }
}
