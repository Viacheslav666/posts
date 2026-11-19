package ru.posts.suggest.sync;

import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.SocialAccountEntity;
import ru.posts.persistence.entity.WorkspaceDataKeyEntity;
import ru.posts.persistence.repository.SocialAccountRepository;
import ru.posts.persistence.repository.WorkspaceDataKeyRepository;
import ru.posts.security.crypto.TokenCryptoService;
import ru.posts.suggest.sync.SuggestSyncService.SuggestTokenResolver;

/** Unseals the channel's user access token. Returns null when the token is missing — never a placeholder. */
public final class VaultSuggestTokenResolver implements SuggestTokenResolver {

    private final SocialAccountRepository socialAccountRepository;
    private final WorkspaceDataKeyRepository keys;
    private final TokenCryptoService crypto;

    public VaultSuggestTokenResolver(
            SocialAccountRepository socialAccountRepository,
            WorkspaceDataKeyRepository keys,
            TokenCryptoService crypto) {
        this.socialAccountRepository = socialAccountRepository;
        this.keys = keys;
        this.crypto = crypto;
    }

    @Override
    public String resolveUserToken(ChannelEntity channel) {
        if (channel == null || channel.getSocialAccountId() == null || channel.getWorkspaceId() == null) {
            return null;
        }
        SocialAccountEntity account =
                socialAccountRepository.findById(channel.getSocialAccountId()).orElse(null);
        if (account == null || account.getAccessTokenEnc() == null || account.getAccessTokenEnc().length == 0) {
            return null;
        }
        WorkspaceDataKeyEntity key = keys.findById(channel.getWorkspaceId()).orElse(null);
        if (key == null || key.getDekEnc() == null) {
            return null;
        }
        try {
            return crypto.unseal(crypto.unwrapDek(key.getDekEnc()), account.getAccessTokenEnc());
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
