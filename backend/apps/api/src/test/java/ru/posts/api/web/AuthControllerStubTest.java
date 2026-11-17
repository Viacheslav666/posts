package ru.posts.api.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import ru.posts.api.error.NotImplementedException;
import ru.posts.api.web.dto.auth.AuthDtos;

class AuthControllerStubTest {

    @Test
    void registerReturnsNotImplemented() {
        AuthController controller = new AuthController();
        assertThatThrownBy(() -> controller.register(
                        new AuthDtos.RegisterRequest("a@b.co", "password123", "A", "Europe/Moscow", "ru")))
                .isInstanceOf(NotImplementedException.class);
    }
}
