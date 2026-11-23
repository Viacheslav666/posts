package ru.posts.api.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import ru.posts.api.auth.AuthProperties;
import ru.posts.api.error.ApiException;

class RestOAuthProviderClientTest {

    private static final String TOKEN_URI = "https://id.vk.ru/oauth2/auth";
    private static final String USER_INFO_URI = "https://id.vk.ru/oauth2/user_info";
    private static final String REDIRECT_URI = "https://socialposts.ru/auth/callback/vk";
    private static final String STATE = "s".repeat(32);

    private MockRestServiceServer server;
    private RestOAuthProviderClient client;

    @BeforeEach
    void setUp() {
        AuthProperties.OAuthProviderProperties vk = new AuthProperties.OAuthProviderProperties();
        vk.setClientId("54917886");
        vk.setClientSecret("must-not-be-sent");
        vk.setTokenUri(TOKEN_URI);
        vk.setUserInfoUri(USER_INFO_URI);
        vk.setRedirectUri(REDIRECT_URI);
        AuthProperties properties = new AuthProperties();
        properties.setOauth(Map.of("vk", vk));

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestOAuthProviderClient(properties, builder);
    }

    @Test
    void vkIdExchangeSendsPkceDeviceIdAndStateThenReadsWrappedUser() {
        MultiValueMap<String, String> tokenForm = new LinkedMultiValueMap<>();
        tokenForm.add("grant_type", "authorization_code");
        tokenForm.add("code", "vk2.a.code");
        tokenForm.add("code_verifier", "verifier");
        tokenForm.add("client_id", "54917886");
        tokenForm.add("device_id", "device-123");
        tokenForm.add("redirect_uri", REDIRECT_URI);
        tokenForm.add("state", STATE);
        server.expect(requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formData(tokenForm))
                .andRespond(withSuccess(
                        """
                        {"access_token":"vk-at","refresh_token":"vk-rt","id_token":"jwt","token_type":"Bearer",
                         "expires_in":3600,"user_id":777,"state":"%s","scope":"vkid.personal_info email"}
                        """
                                .formatted(STATE),
                        MediaType.APPLICATION_JSON));

        MultiValueMap<String, String> userInfoForm = new LinkedMultiValueMap<>();
        userInfoForm.add("client_id", "54917886");
        userInfoForm.add("access_token", "vk-at");
        server.expect(requestTo(USER_INFO_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formData(userInfoForm))
                .andRespond(withSuccess(
                        """
                        {"user":{"user_id":"777","first_name":"Ivan","last_name":"Petrov","email":"ivan@example.ru"}}
                        """,
                        MediaType.APPLICATION_JSON));

        OAuthUserProfile profile = client.exchange(
                "vk", new OAuthCodeExchange("vk2.a.code", "verifier", REDIRECT_URI, STATE, "device-123"));

        server.verify();
        assertThat(profile.provider()).isEqualTo("vk");
        assertThat(profile.providerUserId()).isEqualTo("777");
        assertThat(profile.email()).isEqualTo("ivan@example.ru");
        assertThat(profile.emailVerified()).isTrue();
        assertThat(profile.name()).isEqualTo("Ivan Petrov");
    }

    @Test
    void vkIdProfileWithoutEmailFallsBackToTokenUserId() {
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess(
                        "{\"access_token\":\"vk-at\",\"user_id\":42,\"state\":\"" + STATE + "\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess("{\"user\":{\"first_name\":\"Anna\"}}", MediaType.APPLICATION_JSON));

        OAuthUserProfile profile =
                client.exchange("vk", new OAuthCodeExchange("c", "v", REDIRECT_URI, STATE, "device-1"));

        assertThat(profile.providerUserId()).isEqualTo("42");
        assertThat(profile.email()).isNull();
        assertThat(profile.emailVerified()).isFalse();
        assertThat(profile.name()).isEqualTo("Anna");
    }

    @Test
    void vkIdRequiresDeviceId() {
        assertThatThrownBy(() -> client.exchange("vk", new OAuthCodeExchange("c", "v", REDIRECT_URI, STATE, null)))
                .isInstanceOfSatisfying(
                        ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        server.verify();
    }

    @Test
    void vkIdErrorBodyIsUnauthorized() {
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess(
                        "{\"error\":\"invalid_request\",\"error_description\":\"device_id is invalid\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.exchange("vk", new OAuthCodeExchange("c", "v", REDIRECT_URI, STATE, "d")))
                .isInstanceOfSatisfying(
                        ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void vkIdHttpErrorIsUnauthorized() {
        server.expect(requestTo(TOKEN_URI))
                .andRespond(
                        withBadRequest().contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"invalid_grant\"}"));

        assertThatThrownBy(() -> client.exchange("vk", new OAuthCodeExchange("c", "v", REDIRECT_URI, STATE, "d")))
                .isInstanceOfSatisfying(
                        ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void vkIdStateMismatchIsRejected() {
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess(
                        "{\"access_token\":\"vk-at\",\"user_id\":1,\"state\":\"other\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.exchange("vk", new OAuthCodeExchange("c", "v", REDIRECT_URI, STATE, "d")))
                .isInstanceOfSatisfying(
                        ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
