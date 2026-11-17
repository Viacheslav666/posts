package ru.posts.api.web.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import ru.posts.api.web.dto.common.ApiEnums;

public final class AuthDtos {

    private AuthDtos() {}

    @Schema(name = "RegisterRequest")
    public record RegisterRequest(
            @NotBlank @Email @Schema(example = "user@example.com") String email,
            @NotBlank @Size(min = 10, max = 128) @Schema(example = "correct-horse-battery") String password,
            @NotBlank @Size(min = 1, max = 120) @Schema(example = "Иван Иванов") String name,
            @Schema(example = "Europe/Moscow") String timezone,
            @Schema(example = "ru") String locale) {}

    @Schema(name = "LoginRequest")
    public record LoginRequest(
            @NotBlank @Email @Schema(example = "user@example.com") String email,
            @NotBlank @Schema(example = "correct-horse-battery") String password,
            @Schema(description = "TOTP code when 2FA is enabled", example = "123456") String totpCode) {}

    @Schema(name = "RefreshRequest")
    public record RefreshRequest(
            @Schema(description = "Opaque refresh token when not using httpOnly cookie") String refreshToken) {}

    @Schema(name = "LogoutRequest")
    public record LogoutRequest(
            @Schema(description = "Refresh token to revoke; omit to revoke current session cookie")
                    String refreshToken) {}

    @Schema(name = "TokenResponse")
    public record TokenResponse(
            @Schema(example = "eyJhbGciOiJFUzI1NiJ9...") String accessToken,
            @Schema(example = "Bearer") String tokenType,
            @Schema(description = "Access token TTL in seconds", example = "900") long expiresIn,
            @Schema(description = "Present for non-cookie clients", nullable = true) String refreshToken) {}

    @Schema(name = "OAuthStartResponse")
    public record OAuthStartResponse(
            @Schema(example = "https://id.vk.ru/authorize?...") String authorizationUrl,
            @Schema(example = "xyz") String state) {}

    @Schema(name = "PasswordForgotRequest")
    public record PasswordForgotRequest(@NotBlank @Email @Schema(example = "user@example.com") String email) {}

    @Schema(name = "PasswordResetRequest")
    public record PasswordResetRequest(
            @NotBlank @Schema(description = "Token from email link") String token,
            @NotBlank @Size(min = 10, max = 128) String newPassword) {}

    @Schema(name = "TwoFactorSetupResponse")
    public record TwoFactorSetupResponse(
            @Schema(description = "otpauth:// URI for authenticator apps") String otpauthUrl,
            @Schema(description = "Base32 secret for manual entry") String secret,
            @Schema(description = "One-time backup codes (shown once)") java.util.List<String> backupCodes) {}

    @Schema(name = "TwoFactorConfirmRequest")
    public record TwoFactorConfirmRequest(@NotBlank @Size(min = 6, max = 8) @Schema(example = "123456") String code) {}

    @Schema(name = "TwoFactorDisableRequest")
    public record TwoFactorDisableRequest(
            @NotBlank String password, @NotBlank @Size(min = 6, max = 8) String totpCode) {}

    @Schema(name = "EmailVerifyRequest")
    public record EmailVerifyRequest(@NotBlank String token) {}

    @Schema(name = "SessionDto")
    public record SessionDto(
            UUID id,
            @Schema(example = "Mozilla/5.0...") String userAgent,
            @Schema(example = "203.0.113.10") String ip,
            Instant createdAt,
            Instant lastUsedAt,
            @Schema(description = "True for the session that made this request") boolean current,
            Instant revokedAt) {}

    @Schema(name = "MessageResponse")
    public record MessageResponse(@Schema(example = "ok") String message) {}

    @Schema(name = "OAuthProviderPath")
    public record OAuthProviderPath(
            @Schema(implementation = ApiEnums.AuthProvider.class) ApiEnums.AuthProvider provider) {}
}
