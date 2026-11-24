package ru.posts.grabber.token;

import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.SocialAccountEntity;
import ru.posts.persistence.entity.WorkspaceDataKeyEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.SocialAccountRepository;
import ru.posts.persistence.repository.WorkspaceDataKeyRepository;
import ru.posts.security.crypto.TokenCryptoService;

/**
 * Source walls are read with the configured service token; media is uploaded into the target
 * community with the channel's linked admin token (unsealed from the workspace vault), falling back
 * to the service token when the channel has none.
 */
public final class VaultGrabberTokenProvider implements GrabberTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(VaultGrabberTokenProvider.class);

    private final StaticGrabberTokenProvider serviceToken;
    private final ChannelRepository channels;
    private final SocialAccountRepository socialAccounts;
    private final WorkspaceDataKeyRepository keys;
    private final TokenCryptoService crypto;

    public VaultGrabberTokenProvider(
            String serviceToken,
            ChannelRepository channels,
            SocialAccountRepository socialAccounts,
            WorkspaceDataKeyRepository keys,
            TokenCryptoService crypto) {
        this.serviceToken = new StaticGrabberTokenProvider(serviceToken);
        this.channels = channels;
        this.socialAccounts = socialAccounts;
        this.keys = keys;
        this.crypto = crypto;
    }

    @Override
    public Optional<String> resolveVkToken(UUID workspaceId, UUID targetChannelId) {
        return serviceToken.resolveVkToken(workspaceId, targetChannelId);
    }

    @Override
    public Optional<String> resolveTargetUploadToken(UUID workspaceId, UUID targetChannelId) {
        return channelToken(targetChannelId).or(() -> resolveVkToken(workspaceId, targetChannelId));
    }

    private Optional<String> channelToken(UUID channelId) {
        try {
            ChannelEntity channel =
                    channelId == null ? null : channels.findById(channelId).orElse(null);
            if (channel == null || channel.getSocialAccountId() == null || channel.getWorkspaceId() == null) {
                return Optional.empty();
            }
            SocialAccountEntity account =
                    socialAccounts.findById(channel.getSocialAccountId()).orElse(null);
            if (account == null || account.getAccessTokenEnc() == null || account.getAccessTokenEnc().length == 0) {
                return Optional.empty();
            }
            WorkspaceDataKeyEntity key = keys.findById(channel.getWorkspaceId()).orElse(null);
            if (key == null || key.getDekEnc() == null) {
                return Optional.empty();
            }
            String token = crypto.unseal(crypto.unwrapDek(key.getDekEnc()), account.getAccessTokenEnc());
            return token == null || token.isBlank() ? Optional.empty() : Optional.of(token);
        } catch (RuntimeException ex) {
            log.warn("Grabber upload token unavailable for channel {}: {}", channelId, ex.getMessage());
            return Optional.empty();
        }
    }
}
