package ru.posts.security.password;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PasswordServiceTest {

    private final PasswordService service = new PasswordService(new HibpPasswordChecker.NoOp(), false);

    @Test
    void hashesAndMatches() {
        String hash = service.hash("correct-horse-battery");
        assertThat(service.matches("correct-horse-battery", hash)).isTrue();
        assertThat(service.matches("wrong-password", hash)).isFalse();
    }

    @Test
    void rejectsCommonPasswords() {
        assertThatThrownBy(() -> service.validateStrength("password1234")).isInstanceOf(WeakPasswordException.class);
    }
}
