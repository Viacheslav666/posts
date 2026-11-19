package ru.posts.ingest;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.posts.social.inbound.SocialInboundProcessor;

@Component
public class TelegramAlbumFlushScheduler {

    private final SocialInboundProcessor processor;

    public TelegramAlbumFlushScheduler(SocialInboundProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${posts.social-inbound.album-flush-ms:500}")
    public void flush() {
        processor.flushReadyAlbums();
    }
}
