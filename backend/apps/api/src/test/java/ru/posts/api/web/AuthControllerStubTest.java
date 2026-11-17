package ru.posts.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.posts.api.auth.AuthProperties;
import ru.posts.api.auth.AuthService;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.security.jwt.JwtProperties;

@ExtendWith(MockitoExtension.class)
class AuthControllerStubTest {

    @Mock
    private AuthService authService;

    @Mock
    private AuthProperties authProperties;

    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private AuthController controller;

    @Test
    void registerDelegatesToAuthService() {
        when(authService.register(any())).thenReturn(new AuthDtos.MessageResponse("verification_email_sent"));
        AuthDtos.MessageResponse response = controller.register(
                new AuthDtos.RegisterRequest("a@b.co", "password12345", "A", "Europe/Moscow", "ru"));
        assertThat(response.message()).isEqualTo("verification_email_sent");
    }
}
