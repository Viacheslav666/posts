package ru.posts.social.inbound.album;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

/** Buffer of Telegram media-group fragments shared by every ingest replica. */
public interface AlbumFragmentStore {

    void add(String channelKey, String mediaGroupId, String botId, JsonNode update, Instant receivedAt);

    /** Albums whose newest fragment is at least {@link TelegramAlbumAggregator#WINDOW} old. */
    List<TelegramAlbumAggregator.ReadyAlbum> drainReady(Instant now);
}
