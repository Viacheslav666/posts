package ru.posts.ord.reporting;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.common.uuid.UuidV7;
import ru.posts.ord.api.OrdApiException;
import ru.posts.ord.api.OrdCredentials;
import ru.posts.ord.api.OrdPlatformRequest;
import ru.posts.ord.api.OrdProvider;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.OrdConnectionEntity;
import ru.posts.persistence.entity.OrdPlatformEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.OrdPlatformRepository;

/**
 * Registers a channel as an ORD pad/platform on first advertising post. Idempotent by {@code
 * external_id = platform:{network}:{channelExternalId}}.
 */
@Profile("!openapi")
@Service
public class OrdPlatformService {

    private final OrdPlatformRepository platformRepository;
    private final ChannelRepository channelRepository;
    private final OrdApiKeyResolver apiKeyResolver;

    public OrdPlatformService(
            OrdPlatformRepository platformRepository,
            ChannelRepository channelRepository,
            OrdApiKeyResolver apiKeyResolver) {
        this.platformRepository = platformRepository;
        this.channelRepository = channelRepository;
        this.apiKeyResolver = apiKeyResolver;
    }

    public static String platformExternalId(String network, String channelExternalId) {
        return "platform:" + network + ":" + channelExternalId;
    }

    @Transactional
    public OrdPlatformEntity ensurePlatform(OrdConnectionEntity connection, OrdProvider provider, UUID channelId) {
        ChannelEntity channel = channelRepository
                .findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelId));
        String externalId = platformExternalId(channel.getNetwork(), channel.getExternalId());
        return platformRepository
                .findByWorkspaceIdAndProviderAndExternalId(
                        connection.getWorkspaceId(), connection.getProvider(), externalId)
                .orElseGet(() -> createAndSync(connection, provider, channel, externalId));
    }

    private OrdPlatformEntity createAndSync(
            OrdConnectionEntity connection, OrdProvider provider, ChannelEntity channel, String externalId) {
        Instant now = Instant.now();
        OrdPlatformEntity entity = new OrdPlatformEntity();
        entity.setId(UuidV7.generate());
        entity.setWorkspaceId(connection.getWorkspaceId());
        entity.setProvider(connection.getProvider());
        entity.setExternalId(externalId);
        entity.setName(channel.getTitle() == null ? channel.getExternalId() : channel.getTitle());
        entity.setUrl(channelUrl(channel));
        entity.setNetwork(channel.getNetwork());
        entity.setMeta(Map.of("channelId", channel.getId().toString()));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        String apiKey = apiKeyResolver.resolve(connection.getWorkspaceId(), connection.getApiKeyEnc());
        if (apiKey != null && !apiKey.isBlank()) {
            try {
                provider.upsertPlatform(
                        new OrdCredentials(apiKey),
                        new OrdPlatformRequest(
                                externalId, entity.getName(), entity.getUrl(), channel.getNetwork(), true));
            } catch (OrdApiException ex) {
                // Persist locally even if ORD pad upsert fails — statistics can still reference pad id;
                // monthly job will surface provider errors.
                entity.setMeta(Map.of(
                        "channelId", channel.getId().toString(),
                        "lastError", ex.errorClass().name()));
            }
        }
        return platformRepository.save(entity);
    }

    private static String channelUrl(ChannelEntity channel) {
        String network = channel.getNetwork() == null ? "" : channel.getNetwork();
        String externalId = channel.getExternalId() == null ? "" : channel.getExternalId();
        return switch (network) {
            case "vk" -> "https://vk.com/club" + externalId.replace("-", "");
            case "ok" -> "https://ok.ru/group/" + externalId;
            case "telegram" -> "https://t.me/" + externalId;
            default -> "https://posts.local/" + network + "/" + externalId;
        };
    }
}
