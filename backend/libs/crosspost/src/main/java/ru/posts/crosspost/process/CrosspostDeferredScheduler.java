package ru.posts.crosspost.process;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.posts.persistence.entity.CrosspostItemEntity;
import ru.posts.persistence.repository.CrosspostItemRepository;

/** Drains deferred crosspost_items after the L2 15s race window. */
@Profile("!openapi")
@Component
public class CrosspostDeferredScheduler {

    private static final Logger log = LoggerFactory.getLogger(CrosspostDeferredScheduler.class);

    private final CrosspostItemRepository itemRepository;
    private final CrosspostItemProcessor processor;

    public CrosspostDeferredScheduler(CrosspostItemRepository itemRepository, CrosspostItemProcessor processor) {
        this.itemRepository = itemRepository;
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${posts.crosspost.defer-poll-ms:2000}")
    public void poll() {
        List<CrosspostItemEntity> due = itemRepository.findDueReceived(Instant.now(), PageRequest.of(0, 50));
        for (CrosspostItemEntity item : due) {
            try {
                processor.processDue(item);
            } catch (Exception ex) {
                log.warn("Crosspost deferred processing failed item={}: {}", item.getId(), ex.toString());
                item.setStatus("failed");
                item.setReasons(new String[] {"processor_error"});
                item.setUpdatedAt(Instant.now());
                itemRepository.save(item);
            }
        }
    }
}
