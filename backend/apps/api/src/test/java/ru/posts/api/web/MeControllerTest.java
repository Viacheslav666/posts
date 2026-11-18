package ru.posts.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.posts.api.auth.AuthService;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.user.UserDtos;
import ru.posts.security.auth.PostsPrincipal;
import ru.posts.security.auth.SecurityAuthSupport;

@ExtendWith(MockitoExtension.class)
class MeControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private MeController controller;

    @Test
    void meDelegatesToAuthService() {
        UUID userId = UUID.fromString("019a0000-0002-7000-8000-000000000001");
        PostsPrincipal principal = new PostsPrincipal(userId, null);
        UserDtos.UserProfile expected = new UserDtos.UserProfile(
                userId,
                "user@example.com",
                true,
                "User",
                null,
                "Europe/Moscow",
                "ru",
                false,
                Instant.parse("2026-11-01T10:00:00Z"),
                List.of(new UserDtos.MembershipSummary(
                        UUID.fromString("019a0000-0002-7000-8000-000000000002"),
                        "Личное пространство",
                        ApiEnums.MemberRole.owner,
                        ApiEnums.BillingStatus.trial)),
                false,
                java.util.Map.of(),
                null,
                List.of());
        when(authService.me(principal)).thenReturn(expected);

        try (MockedStatic<SecurityAuthSupport> mocked = Mockito.mockStatic(SecurityAuthSupport.class)) {
            mocked.when(SecurityAuthSupport::requirePrincipal).thenReturn(principal);
            UserDtos.UserProfile profile = controller.me();
            assertThat(profile.email()).isEqualTo("user@example.com");
            assertThat(profile.memberships()).hasSize(1);
        }
    }
}
