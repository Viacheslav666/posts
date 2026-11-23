package ru.posts.emulator.oauth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OAuthStubsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void vkIdTokenAndUserInfo() throws Exception {
        mockMvc.perform(post("/oauth2/auth")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("grant_type=authorization_code&code=x&code_verifier=v&client_id=1"
                                + "&device_id=d&redirect_uri=http://localhost:3000/auth/callback/vk&state=st"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists())
                .andExpect(jsonPath("$.state").value("st"));

        mockMvc.perform(post("/oauth2/user_info")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("client_id=1&access_token=emulator-vk-id-access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.user_id").value("10001"));

        mockMvc.perform(get("/oauth2/user_info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.user_id").value("10001"));
    }

    @Test
    void vkIdAuthorizationCodeRequiresDeviceId() throws Exception {
        mockMvc.perform(post("/oauth2/auth")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("grant_type=authorization_code&code=x&code_verifier=v&client_id=1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void vkIdRefreshGrantStillWorks() throws Exception {
        mockMvc.perform(post("/oauth2/auth")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("grant_type=refresh_token&refresh_token=r&client_id=1&device_id=d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists());
    }

    @Test
    void yandexStub() throws Exception {
        mockMvc.perform(post("/yandex/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("grant_type=authorization_code&code=x"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists());

        mockMvc.perform(get("/yandex/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.default_email").exists());
    }
}
