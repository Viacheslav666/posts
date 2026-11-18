package ru.posts.ord.reporting.impressions;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import ru.posts.ord.reporting.OrdChannelTokenResolver;
import ru.posts.ord.reporting.config.OrdReportingProperties;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.PublicationEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.social.api.ChannelContext;
import ru.posts.social.api.Network;
import ru.posts.social.api.SocialAdapter;

@Profile("!openapi")
@Component
public class OrdImpressionsFetcher {

    public record Impression(long count, String source) {}

    private final OrdReportingProperties properties;
    private final OrdChannelTokenResolver tokenResolver;
    private final ChannelRepository channelRepository;
    private final Map<String, SocialAdapter> adaptersByNetwork;

    public OrdImpressionsFetcher(
            OrdReportingProperties properties,
            OrdChannelTokenResolver tokenResolver,
            ChannelRepository channelRepository,
            List<SocialAdapter> adapters) {
        this.properties = properties;
        this.tokenResolver = tokenResolver;
        this.channelRepository = channelRepository;
        this.adaptersByNetwork =
                adapters.stream().collect(Collectors.toMap(a -> a.network().code(), Function.identity(), (a, b) -> a));
    }

    public Impression fetch(PublicationEntity publication, boolean story) {
        if (properties.getImpressionsOverride() != null) {
            return new Impression(properties.getImpressionsOverride(), "override");
        }
        if (publication.getRemotePostId() == null
                || publication.getRemotePostId().isBlank()) {
            return new Impression(0, "unavailable");
        }
        ChannelEntity channel =
                channelRepository.findById(publication.getChannelId()).orElse(null);
        if (channel == null) {
            return new Impression(0, "unavailable");
        }
        SocialAdapter adapter = adaptersByNetwork.get(channel.getNetwork());
        if (adapter == null) {
            return new Impression(0, "unavailable");
        }
        Optional<String> token = tokenResolver.resolveAccessToken(publication.getWorkspaceId(), channel.getId());
        if (token.isEmpty()) {
            return new Impression(0, "unavailable");
        }
        ChannelContext ctx = new ChannelContext(
                publication.getWorkspaceId(),
                channel.getId(),
                channel.getExternalId(),
                token.get(),
                Network.fromCode(channel.getNetwork()));
        Optional<Long> views = story
                ? adapter.fetchStoryImpressions(ctx, publication.getRemotePostId())
                : adapter.fetchImpressions(ctx, publication.getRemotePostId());
        if (views.isEmpty()) {
            return new Impression(0, "unavailable");
        }
        String source =
                switch (channel.getNetwork() == null ? "" : channel.getNetwork()) {
                    case "vk" -> story ? "story_api" : "vk_api";
                    case "ok" -> story ? "story_api" : "ok_api";
                    default -> "unavailable";
                };
        return new Impression(views.get(), source);
    }
}
