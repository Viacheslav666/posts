package ru.posts.api.auth;

import java.util.UUID;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.user.UserDtos;
import ru.posts.security.auth.PostsPrincipal;

public interface AuthService {

    AuthDtos.MessageResponse register(AuthDtos.RegisterRequest request);

    AuthDtos.MessageResponse verifyEmail(AuthDtos.EmailVerifyRequest request);

    TokenPair login(AuthDtos.LoginRequest request, ClientRequestContext ctx);

    TokenPair refresh(String refreshToken, ClientRequestContext ctx);

    AuthDtos.MessageResponse logout(String refreshToken);

    AuthDtos.OAuthStartResponse oauthStart(ApiEnums.AuthProvider provider);

    TokenPair oauthCallback(ApiEnums.AuthProvider provider, String code, String state, ClientRequestContext ctx);

    AuthDtos.MessageResponse oauthLink(AuthDtos.OAuthLinkRequest request, UUID userId);

    AuthDtos.TwoFactorSetupResponse twoFactorSetup(UUID userId);

    AuthDtos.MessageResponse twoFactorConfirm(UUID userId, AuthDtos.TwoFactorConfirmRequest request);

    AuthDtos.MessageResponse twoFactorDisable(UUID userId, AuthDtos.TwoFactorDisableRequest request);

    AuthDtos.MessageResponse passwordForgot(AuthDtos.PasswordForgotRequest request);

    AuthDtos.MessageResponse passwordReset(AuthDtos.PasswordResetRequest request);

    UserDtos.UserProfile me(PostsPrincipal principal);

    UserDtos.UserProfile updateMe(UUID userId, UserDtos.UpdateMeRequest request);

    AuthDtos.MessageResponse changePassword(UUID userId, UUID currentSessionId, UserDtos.UpdatePasswordRequest request);

    AuthDtos.MessageResponse deleteMe(UUID userId);

    CursorPage.SessionPage listSessions(UUID userId, UUID currentSessionId, CursorPage.Params page);

    AuthDtos.MessageResponse revokeSession(UUID userId, UUID sessionId);

    AuthDtos.MessageResponse revokeOtherSessions(UUID userId, UUID currentSessionId);
}
