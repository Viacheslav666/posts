package ru.posts.scheduler.dispatch;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DispatchScheduler {

    private final PublicationDispatchService dispatchService;
    private final GrabberDispatchService grabberDispatchService;
    private final OutboxRelayService outboxRelayService;

    public DispatchScheduler(
            PublicationDispatchService dispatchService,
            GrabberDispatchService grabberDispatchService,
            OutboxRelayService outboxRelayService) {
        this.dispatchService = dispatchService;
        this.grabberDispatchService = grabberDispatchService;
        this.outboxRelayService = outboxRelayService;
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

    /** BE-11: relay unpublished outbox rows to notify exchange. */
    @Scheduled(fixedDelayString = "${posts.scheduler.outbox-relay-interval-ms:1000}")
    public void relayOutbox() {
        outboxRelayService.relayBatch();
    }

    @Scheduled(fixedDelayString = "${posts.scheduler.reaper-interval-ms:30000}")
    @SchedulerLock(name = "publicationReaper", lockAtMostFor = "25s", lockAtLeastFor = "5s")
    public void reaper() {
        dispatchService.reapStuck();
    }
}
