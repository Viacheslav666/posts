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
                        .content("grant_type=authorization_code&code=x"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists());

        mockMvc.perform(get("/oauth2/user_info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id").value("10001"));
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