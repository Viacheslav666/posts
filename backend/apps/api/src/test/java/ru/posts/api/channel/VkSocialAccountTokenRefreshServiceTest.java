package ru.posts.api.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.posts.persistence.entity.SocialAccountEntity;
import ru.posts.persistence.entity.WorkspaceDataKeyEntity;
import ru.posts.persistence.repository.SocialAccountRepository;
import ru.posts.persistence.repository.WorkspaceDataKeyRepository;
import ru.posts.security.crypto.TokenCryptoService;
import ru.posts.security.oauth.VkChannelConnectProperties;
import ru.posts.security.oauth.VkIdUserTokenRefresher;

@ExtendWith(MockitoExtension.class)
class VkSocialAccountTokenRefreshServiceTest {

    private static final String TOKEN_URI = "https://id.vk.ru/oauth2/auth";

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private WorkspaceDataKeyRepository keys;

    private TokenCryptoService crypto;
    private VkIdUserTokenRefresher refresher;
    private MockRestServiceServer server;
    private UUID accountId;
    private UUID workspaceId;
    private SocialAccountEntity account;

    @BeforeEach
    void setUp() {
        crypto = TokenCryptoService.fromKeyMaterial("vk-refresh-test");
        byte[] dek = crypto.generateDek();
        workspaceId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        WorkspaceDataKeyEntity key = new WorkspaceDataKeyEntity();
        key.setWorkspaceId(workspaceId);
        key.setDekEnc(crypto.wrapDek(dek));
        when(keys.findById(workspaceId)).thenReturn(Optional.of(key));

        VkChannelConnectProperties properties = new VkChannelConnectProperties();
        properties.setVkTokenUri(TOKEN_URI);
        properties.setVkAuthorizationUri("https://id.vk.ru/authorize");
        properties.setVkClientId("54917886");

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        refresher = new VkIdUserTokenRefresher(properties, socialAccountRepository, keys, crypto, builder.build());

        account = new SocialAccountEntity();
        account.setId(accountId);
        account.setWorkspaceId(workspaceId);
        account.setNetwork("vk");
        account.setDeviceId("device-9");
        account.setTokenExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        account.setRefreshTokenEnc(crypto.seal(dek, "old-rt"));
        account.setAccessTokenEnc(crypto.seal(dek, "old-at"));
        when(socialAccountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));
        lenient().when(socialAccountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void refreshSendsStateAndRotatesTokensOnce() {
        server.expect(requestTo(TOKEN_URI))
                .andExpect(request -> {
                    String body = request.getBody().toString();
                    assertThat(body).contains("grant_type=refresh_token");
                    assertThat(body).contains("device_id=device-9");
                    assertThat(body).contains("client_id=54917886");
                    assertThat(body).contains("state=");
                    assertThat(body).doesNotContain("client_secret");
                })
                .andRespond(request -> {
                    String sent = request.getBody().toString();
                    String state = sent.replaceAll(".*state=([^&]+).*", "$1");
                    return withSuccess(
                                    """
                                    {"access_token":"new-at","refresh_token":"new-rt","expires_in":3600,"state":"%s"}
                                    """
                                            .formatted(state),
                                    MediaType.APPLICATION_JSON)
                            .createResponse(request);
                });

        VkSocialAccountTokenRefreshService service = new VkSocialAccountTokenRefreshService(refresher);
        SocialAccountEntity fresh = service.ensureFresh(account);
        service.ensureFresh(fresh);

        server.verify();
        verify(socialAccountRepository, times(1)).save(any());
        assertThat(fresh.getTokenExpiresAt()).isAfter(Instant.now().plus(30, ChronoUnit.MINUTES));
    }

    @Test
    void echoedStateMismatchIsRejected() {
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess(
                        "{\"access_token\":\"new-at\",\"refresh_token\":\"new-rt\",\"expires_in\":3600,\"state\":\"other\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> refresher.ensureFresh(accountId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("state mismatch");
        verify(socialAccountRepository, times(0)).save(any());
    }
}
