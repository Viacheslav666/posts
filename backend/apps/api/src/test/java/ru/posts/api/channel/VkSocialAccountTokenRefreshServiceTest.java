package ru.posts.api.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.posts.persistence.entity.SocialAccountEntity;
import ru.posts.persistence.repository.SocialAccountRepository;

@ExtendWith(MockitoExtension.class)
class VkSocialAccountTokenRefreshServiceTest {

    @Mock
    private ChannelProperties properties;

    @Mock
    private SocialConnectClient connectClient;

    @Mock
    private WorkspaceTokenVault tokenVault;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    private VkSocialAccountTokenRefreshService service;

    @BeforeEach
    void setUp() {
        service =
                new VkSocialAccountTokenRefreshService(properties, connectClient, tokenVault, socialAccountRepository);
    }

    @Test
    void refreshesExpiredVkIdToken() {
        UUID workspaceId = UUID.randomUUID();
        SocialAccountEntity account = new SocialAccountEntity();
        account.setNetwork("vk");
        account.setWorkspaceId(workspaceId);
        account.setTokenExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        account.setDeviceId("device-9");
        account.setRefreshTokenEnc(new byte[] {1});
        account.setAccessTokenEnc(new byte[] {2});

        when(properties.vkIdMode()).thenReturn(true);
        when(tokenVault.unseal(workspaceId, account.getRefreshTokenEnc())).thenReturn("refresh");
        when(connectClient.refreshVk("refresh", "device-9", null))
                .thenReturn(new SocialConnectClient.OAuthToken(
                        "new-at", "new-rt", Instant.now().plusSeconds(3600), "1", "n"));
        when(tokenVault.seal(eq(workspaceId), eq("new-at"))).thenReturn(new byte[] {3});
        when(tokenVault.seal(eq(workspaceId), eq("new-rt"))).thenReturn(new byte[] {4});
        when(socialAccountRepository.save(account)).thenReturn(account);

        SocialAccountEntity fresh = service.ensureFresh(account);

        assertThat(fresh.getAccessTokenEnc()).containsExactly(3);
        verify(socialAccountRepository).save(account);
    }

    @Test
    void skipsRefreshWhenStillValid() {
        SocialAccountEntity account = new SocialAccountEntity();
        account.setNetwork("vk");
        account.setTokenExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));
        when(properties.vkIdMode()).thenReturn(true);

        assertThat(service.ensureFresh(account)).isSameAs(account);
        verify(connectClient, never()).refreshVk(any(), any(), any());
    }
}
