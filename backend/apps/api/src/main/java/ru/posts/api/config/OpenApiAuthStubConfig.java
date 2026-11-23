package ru.posts.api.config;

import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import ru.posts.api.auth.AuthService;
import ru.posts.api.auth.ClientRequestContext;
import ru.posts.api.auth.TokenPair;
import ru.posts.api.error.NotImplementedException;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.user.UserDtos;

/** Beans required to boot the API under the `openapi` profile without a database. */
@Configuration
@Profile("openapi")
public class OpenApiAuthStubConfig {

    @Bean
    AuthService authService() {
        return new AuthService() {
            @Override
            public AuthDtos.MessageResponse register(AuthDtos.RegisterRequest request) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public AuthDtos.MessageResponse verifyEmail(AuthDtos.EmailVerifyRequest request) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public TokenPair login(AuthDtos.LoginRequest request, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public TokenPair refresh(String refreshToken, ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public AuthDtos.MessageResponse logout(String refreshToken) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public AuthDtos.OAuthStartResponse oauthStart(ApiEnums.AuthProvider provider) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public TokenPair oauthCallback(
                    ApiEnums.AuthProvider provider,
                    String code,
                    String state,
                    String deviceId,
                    ClientRequestContext ctx) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public AuthDtos.MessageResponse oauthLink(AuthDtos.OAuthLinkRequest request, UUID userId) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public AuthDtos.TwoFactorSetupResponse twoFactorSetup(UUID userId) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public AuthDtos.MessageResponse twoFactorConfirm(UUID userId, AuthDtos.TwoFactorConfirmRequest request) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public AuthDtos.MessageResponse twoFactorDisable(UUID userId, AuthDtos.TwoFactorDisableRequest request) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public AuthDtos.MessageResponse passwordForgot(AuthDtos.PasswordForgotRequest request) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public AuthDtos.MessageResponse passwordReset(AuthDtos.PasswordResetRequest request) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public UserDtos.UserProfile me(ru.posts.security.auth.PostsPrincipal principal) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public UserDtos.UserProfile updateMe(UUID userId, UserDtos.UpdateMeRequest request) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public AuthDtos.MessageResponse changePassword(
                    UUID userId, UUID currentSessionId, UserDtos.UpdatePasswordRequest request) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public AuthDtos.MessageResponse deleteMe(UUID userId) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public CursorPage.SessionPage listSessions(UUID userId, UUID currentSessionId, CursorPage.Params page) {
                return new CursorPage.SessionPage(List.of(), null, false);
            }

            @Override
            public AuthDtos.MessageResponse revokeSession(UUID userId, UUID sessionId) {
                throw new NotImplementedException("openapi");
            }

            @Override
            public AuthDtos.MessageResponse revokeOtherSessions(UUID userId, UUID currentSessionId) {
                throw new NotImplementedException("openapi");
            }
        };
    }
}
