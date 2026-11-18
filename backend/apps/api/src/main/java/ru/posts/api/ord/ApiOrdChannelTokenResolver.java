package ru.posts.api.ord;

import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import ru.posts.api.channel.WorkspaceTokenVault;
import ru.posts.ord.reporting.OrdChannelTokenResolver;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.SocialAccountEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.SocialAccountRepository;

/** Resolves social channel tokens for ORD impression fetches (API app). */
@Component
@Primary
@Profile("!openapi")
public class ApiOrdChannelTokenResolver implements OrdChannelTokenResolver {

    private final ChannelRepository channelRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final WorkspaceTokenVault tokenVault;

    public ApiOrdChannelTokenResolver(
            ChannelRepository channelRepository,
            SocialAccountRepository socialAccountRepository,
            WorkspaceTokenVault tokenVault) {
        this.channelRepository = channelRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.tokenVault = tokenVault;
    }

    @Override
    public Optional<String> resolveAccessToken(UUID workspaceId, UUID channelId) {
        ChannelEntity channel = channelRepository.findById(channelId).orElse(null);
        if (channel == null || channel.getSocialAccountId() == null) {
            return Optional.empty();
        }
        SocialAccountEntity account =
                socialAccountRepository.findById(channel.getSocialAccountId()).orElse(null);
        if (account == null || account.getAccessTokenEnc() == null) {
            return Optional.empty();
        }
        String token = tokenVault.unseal(workspaceId, account.getAccessTokenEnc());
        return token == null || token.isBlank() ? Optional.empty() : Optional.of(token);
    }
}
