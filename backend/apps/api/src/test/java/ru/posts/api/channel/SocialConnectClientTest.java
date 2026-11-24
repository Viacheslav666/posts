package ru.posts.api.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SocialConnectClientTest {

    private static final String VK_ID_TOKEN = "https://id.vk.ru/oauth2/auth";
    private static final String VK_API = "https://api.vk.com";

    private ChannelProperties properties;
    private MockRestServiceServer socialServer;
    private MockRestServiceServer vkApiServer;
    private SocialConnectClient client;

    @BeforeEach
    void setUp() {
        properties = new ChannelProperties();
        properties.setSocialBaseUrl("http://emulator:8090");
        properties.setVkAuthorizationUri("https://id.vk.ru/authorize");
        properties.setVkTokenUri(VK_ID_TOKEN);
        properties.setVkClientId("54917886");
        properties.setVkApiBaseUrl(VK_API);

        RestClient.Builder socialBuilder = RestClient.builder().baseUrl("http://emulator:8090");
        socialServer = MockRestServiceServer.bindTo(socialBuilder).build();

        RestClient.Builder vkBuilder = RestClient.builder().baseUrl(VK_API);
        vkApiServer = MockRestServiceServer.bindTo(vkBuilder).build();

        client = new SocialConnectClientForTest(properties, socialBuilder.build(), vkBuilder.build());
    }

    @Test
    void vkIdExchangeOmitsClientSecretAndSendsDeviceId() {
        socialServer
                .expect(requestTo(VK_ID_TOKEN))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(request -> {
                    String body = request.getBody().toString();
                    assertThat(body).contains("device_id=device-1");
                    assertThat(body).contains("code_verifier=verifier");
                    assertThat(body).doesNotContain("client_secret");
                })
                .andRespond(withSuccess(
                        """
                        {"access_token":"at","refresh_token":"rt","expires_in":3600,"user_id":"42","state":"st"}
                        """,
                        MediaType.APPLICATION_JSON));

        vkApiServer
                .expect(requestTo(VK_API + "/method/users.get"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {"response":[{"id":42,"first_name":"Ivan","last_name":"P"}]}
                        """,
                        MediaType.APPLICATION_JSON));

        SocialConnectClient.OAuthToken token = client.exchangeVk(
                "code", "verifier", "https://socialposts.ru/channels/connect/vk/callback", "device-1", "st");

        socialServer.verify();
        assertThat(token.accessToken()).isEqualTo("at");
        assertThat(token.externalUserId()).isEqualTo("42");
        assertThat(token.displayName()).isEqualTo("Ivan P");
    }

    @Test
    void vkIdRefreshRotatesTokens() {
        socialServer
                .expect(requestTo(VK_ID_TOKEN))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> assertThat(request.getBody().toString()).contains("grant_type=refresh_token"))
                .andRespond(withSuccess(
                        """
                        {"access_token":"new-at","refresh_token":"new-rt","expires_in":3600,"user_id":"42"}
                        """,
                        MediaType.APPLICATION_JSON));

        SocialConnectClient.OAuthToken token = client.refreshVk("old-rt", "device-1", null);
        assertThat(token.accessToken()).isEqualTo("new-at");
        assertThat(token.refreshToken()).isEqualTo("new-rt");
    }

    @Test
    void parseRealVkGroupsResponse() {
        vkApiServer
                .expect(requestTo(VK_API + "/method/groups.get"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {"response":{"count":1,"items":[{"id":1,"name":"G","screen_name":"g",
                        "photo_100":"https://p","type":"group","is_admin":1,"admin_level":3}]}}
                        """,
                        MediaType.APPLICATION_JSON));

        vkApiServer
                .expect(requestTo(VK_API + "/method/users.get"))
                .andRespond(withSuccess(
                        "{\"response\":[{\"id\":9,\"first_name\":\"A\",\"last_name\":\"B\"}]}",
                        MediaType.APPLICATION_JSON));
        vkApiServer
                .expect(requestTo(VK_API + "/method/users.get"))
                .andRespond(withSuccess("{\"response\":[{\"id\":9}]}", MediaType.APPLICATION_JSON));

        List<SocialConnectClient.RemoteChannel> targets = client.listVkAdminTargets("user-token");
        assertThat(targets).hasSize(2);
        assertThat(targets.get(0).groupToken()).isNull();
        assertThat(targets.get(0).title()).isEqualTo("G");
        assertThat(targets.get(1).type()).isEqualTo("profile");
    }

    @Test
    void emulatorExchangeStillUsesClientSecret() {
        ChannelProperties emulatorProps = new ChannelProperties();
        emulatorProps.setSocialBaseUrl("http://emulator:8090");
        emulatorProps.setVkTokenUri("http://emulator:8090/oauth/vk/token");
        emulatorProps.setVkAuthorizationUri("http://emulator:8090/oauth/vk/authorize");
        RestClient.Builder socialBuilder = RestClient.builder().baseUrl("http://emulator:8090");
        MockRestServiceServer emulatorServer =
                MockRestServiceServer.bindTo(socialBuilder).build();
        SocialConnectClient emulatorClient =
                new SocialConnectClientForTest(emulatorProps, socialBuilder.build(), socialBuilder.build());

        emulatorServer
                .expect(requestTo("http://emulator:8090/oauth/vk/token"))
                .andExpect(request -> assertThat(request.getBody().toString()).contains("client_secret"))
                .andRespond(withSuccess(
                        """
                        {"access_token":"at","refresh_token":"rt","expires_in":3600,"user_id":"1","name":"N"}
                        """,
                        MediaType.APPLICATION_JSON));

        SocialConnectClient.OAuthToken token = emulatorClient.exchangeVk("c", "v", "http://app/cb", null, null);
        assertThat(token.accessToken()).isEqualTo("at");
        emulatorServer.verify();
    }

    static final class SocialConnectClientForTest extends SocialConnectClient {
        SocialConnectClientForTest(ChannelProperties properties, RestClient social, RestClient vkApi) {
            super(properties);
            inject(social, vkApi);
        }

        private void inject(RestClient social, RestClient vkApi) {
            try {
                var socialField = SocialConnectClient.class.getDeclaredField("socialClient");
                socialField.setAccessible(true);
                socialField.set(this, social);
                var vkField = SocialConnectClient.class.getDeclaredField("vkApiClient");
                vkField.setAccessible(true);
                vkField.set(this, vkApi);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
