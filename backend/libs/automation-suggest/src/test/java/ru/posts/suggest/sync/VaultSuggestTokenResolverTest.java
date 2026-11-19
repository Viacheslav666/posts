package ru.posts.suggest.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.SocialAccountEntity;
import ru.posts.persistence.entity.WorkspaceDataKeyEntity;
import ru.posts.persistence.repository.SocialAccountRepository;
import ru.posts.persistence.repository.WorkspaceDataKeyRepository;
import ru.posts.security.crypto.TokenCryptoService;

@ExtendWith(MockitoExtension.class)
class VaultSuggestTokenResolverTest {

    @Mock
    SocialAccountRepository socialAccountRepository;

    @Mock
    WorkspaceDataKeyRepository keys;

    @Test
    void missingOrUndecryptableTokenIsNullNotTheTestPlaceholder() {
        TokenCryptoService crypto = TokenCryptoService.fromKeyMaterial("suggest-kek");
        byte[] dek = crypto.generateDek();
        UUID workspaceId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        WorkspaceDataKeyEntity key = new WorkspaceDataKeyEntity();
        key.setWorkspaceId(workspaceId);
        key.setDekEnc(crypto.wrapDek(dek));
        when(keys.findById(workspaceId)).thenReturn(Optional.of(key));

        SocialAccountEntity account = new SocialAccountEntity();
        account.setId(accountId);
        account.setAccessTokenEnc(crypto.seal(dek, "user-token"));
        when(socialAccountRepository.findById(accountId)).thenReturn(Optional.of(account));

        VaultSuggestTokenResolver resolver = new VaultSuggestTokenResolver(socialAccountRepository, keys, crypto);
        ChannelEntity channel = new ChannelEntity();
        channel.setWorkspaceId(workspaceId);
        channel.setSocialAccountId(accountId);
        assertThat(resolver.resolveUserToken(channel)).isEqualTo("user-token");

        account.setAccessTokenEnc(null);
        assertThat(resolver.resolveUserToken(channel)).isNull();

        channel.setSocialAccountId(null);
        assertThat(resolver.resolveUserToken(channel)).isNull();
    }
}
