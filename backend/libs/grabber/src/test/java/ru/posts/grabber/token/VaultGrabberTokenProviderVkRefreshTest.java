package ru.posts.grabber.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.SocialAccountRepository;
import ru.posts.persistence.repository.WorkspaceDataKeyRepository;
import ru.posts.security.crypto.TokenCryptoService;
import ru.posts.security.oauth.VkIdUserTokenRefresher;

class VaultGrabberTokenProviderVkRefreshTest {

    @Test
    void uploadTokenUsesRefreshedVkIdUserToken() {
        UUID channelId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        ChannelEntity channel = new ChannelEntity();
        channel.setId(channelId);
        channel.setWorkspaceId(workspaceId);
        channel.setSocialAccountId(accountId);

        ChannelRepository channels = mock(ChannelRepository.class);
        when(channels.findById(channelId)).thenReturn(Optional.of(channel));
        VkIdUserTokenRefresher refresher = mock(VkIdUserTokenRefresher.class);
        when(refresher.refreshedAccessToken(accountId)).thenReturn(Optional.of("fresh-upload-token"));

        VaultGrabberTokenProvider provider = new VaultGrabberTokenProvider(
                "service",
                channels,
                mock(SocialAccountRepository.class),
                mock(WorkspaceDataKeyRepository.class),
                mock(TokenCryptoService.class),
                refresher);

        assertThat(provider.resolveTargetUploadToken(workspaceId, channelId)).contains("fresh-upload-token");
    }
}
