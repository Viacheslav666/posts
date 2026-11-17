package ru.posts.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import ru.posts.api.error.NotImplementedException;
import ru.posts.api.web.dto.user.UserDtos;
import ru.posts.api.web.support.LocalFixtures;

@ExtendWith(MockitoExtension.class)
class MeControllerTest {

    @Mock
    private Environment environment;

    @Test
    void localProfileReturnsFixture() {
        when(environment.matchesProfiles("local")).thenReturn(true);
        UserDtos.UserProfile profile = new MeController(environment).me();
        assertThat(profile.email()).isEqualTo("local@posts.test");
        assertThat(profile.id()).isEqualTo(LocalFixtures.LOCAL_USER_ID);
        assertThat(profile.memberships()).hasSize(1);
    }

    @Test
    void nonLocalProfileReturns501() {
        when(environment.matchesProfiles("local")).thenReturn(false);
        assertThatThrownBy(() -> new MeController(environment).me())
                .isInstanceOf(NotImplementedException.class)
                .hasMessageContaining("meGet");
    }
}
