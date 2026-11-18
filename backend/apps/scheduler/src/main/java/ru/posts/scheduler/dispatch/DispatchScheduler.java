package ru.posts.scheduler.dispatch;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DispatchScheduler {

    private final PublicationDispatchService dispatchService;
    private final GrabberDispatchService grabberDispatchService;

    public DispatchScheduler(
            PublicationDispatchService dispatchService, GrabberDispatchService grabberDispatchService) {
        this.dispatchService = dispatchService;
        this.grabberDispatchService = grabberDispatchService;
    }

    /** Competitive claim — no ShedLock; SKIP LOCKED coordinates instances. */
    @Scheduled(fixedDelayString = "${posts.scheduler.dispatch-interval-ms:1000}")
    public void dispatch() {
        dispatchService.dispatchDueBatch();
    }

    @Scheduled(fixedDelayString = "${posts.scheduler.dispatch-interval-ms:1000}")
    public void dispatchActions() {
        dispatchService.dispatchDueActions();
    }

    /** BE-17: claim due grabber/rss rules and enqueue ingest.grabber. */
    @Scheduled(fixedDelayString = "${posts.scheduler.grabber-dispatch-interval-ms:15000}")
    public void dispatchGrabber() {
        grabberDispatchService.dispatchDueRules();
    }

    @Scheduled(fixedDelayString = "${posts.scheduler.reaper-interval-ms:30000}")
    @SchedulerLock(name = "publicationReaper", lockAtMostFor = "25s", lockAtLeastFor = "5s")
    public void reaper() {
        dispatchService.reapStuck();
    }
}
