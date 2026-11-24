package ru.posts.publisher.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import ru.posts.security.crypto.TokenCryptoService;
import ru.posts.security.oauth.VkIdUserTokenRefresher;
import ru.posts.social.api.AccessTokenKind;
import ru.posts.social.api.Network;

class ChannelAccessResolverVkRefreshTest {

    @Test
    void userTokenComesFromVkIdRefreshBeforeUnseal() {
        UUID accountId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        VkIdUserTokenRefresher refresher = mock(VkIdUserTokenRefresher.class);
        when(refresher.refreshedAccessToken(accountId)).thenReturn(Optional.of("fresh-user-token"));

        ChannelAccessResolver resolver =
                new ChannelAccessResolver(
                        mock(JdbcClient.class), mock(TokenCryptoService.class), "fallback", refresher) {
                    @Override
                    protected AccessRow load(UUID id) {
                        return new AccessRow("t", "-1", null, accountId, new byte[] {1}, new byte[] {2});
                    }
                };

        var ctx = resolver.context(UUID.randomUUID(), channelId, "-1", Network.VK, AccessTokenKind.USER);
        assertThat(ctx.accessToken()).isEqualTo("fresh-user-token");
        verify(refresher).refreshedAccessToken(accountId);
    }
}
