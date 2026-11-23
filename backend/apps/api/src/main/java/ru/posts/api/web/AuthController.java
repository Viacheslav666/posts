package ru.posts.api.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.api.auth.AuthCookies;
import ru.posts.api.auth.AuthProperties;
import ru.posts.api.auth.AuthService;
import ru.posts.api.auth.ClientRequestContexts;
import ru.posts.api.auth.TokenPair;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.security.auth.SecurityAuthSupport;
import ru.posts.security.jwt.JwtProperties;

@RestController
@RequestMapping(path = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Auth", description = "Registration, login, OAuth, 2FA, password recovery")
@SecurityRequirements
@OpenApiResponses
public class AuthController {

    private final AuthService authService;
    private final AuthProperties authProperties;
    private final JwtProperties jwtProperties;

    public AuthController(AuthService authService, AuthProperties authProperties, JwtProperties jwtProperties) {
        this.authService = authService;
        this.authProperties = authProperties;
        this.jwtProperties = jwtProperties;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "authRegister",
            summary = "Register with email and password",
            description = "Creates an unverified account and personal workspace (US-1.1).")
    @ApiResponse(
            responseCode = "201",
            description = "Registered",
            content =
                    @Content(
                            schema = @Schema(implementation = AuthDtos.MessageResponse.class),
                            examples = @ExampleObject(value = "{\"message\":\"verification_email_sent\"}")))
    public AuthDtos.MessageResponse register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/email/verify")
    @Operation(operationId = "authVerifyEmail", summary = "Confirm email via token from the verification letter")
    @ApiResponse(responseCode = "200", description = "Email verified")
    public AuthDtos.MessageResponse verifyEmail(@Valid @RequestBody AuthDtos.EmailVerifyRequest request) {
        return authService.verifyEmail(request);
    }

    @PostMapping("/login")
    @Operation(operationId = "authLogin", summary = "Login with email and password (optional TOTP)")
    @ApiResponse(
            responseCode = "200",
            description = "Tokens issued",
            content = @Content(schema = @Schema(implementation = AuthDtos.TokenResponse.class)),
            headers = @Header(name = HttpHeaders.SET_COOKIE, description = "httpOnly refresh cookie for web clients"))
    public AuthDtos.TokenResponse login(
            @Valid @RequestBody AuthDtos.LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        TokenPair pair = authService.login(request, ClientRequestContexts.from(httpRequest));
        AuthCookies.writeRefreshCookie(
                httpResponse,
                authProperties,
                pair.refreshToken(),
                jwtProperties.getRefreshTokenTtl().toSeconds());
        return toTokenResponse(pair);
    }

    @PostMapping("/refresh")
    @Operation(operationId = "authRefresh", summary = "Rotate refresh token and issue a new access token")
    @ApiResponse(
            responseCode = "200",
            description = "Refreshed",
            content = @Content(schema = @Schema(implementation = AuthDtos.TokenResponse.class)))
    public AuthDtos.TokenResponse refresh(
            @Valid @RequestBody(required = false) AuthDtos.RefreshRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String refresh = request != null
                        && request.refreshToken() != null
                        && !request.refreshToken().isBlank()
                ? request.refreshToken()
                : AuthCookies.readRefreshCookie(httpRequest, authProperties);
        TokenPair pair = authService.refresh(refresh, ClientRequestContexts.from(httpRequest));
        AuthCookies.writeRefreshCookie(
                httpResponse,
                authProperties,
                pair.refreshToken(),
                jwtProperties.getRefreshTokenTtl().toSeconds());
        return toTokenResponse(pair);
    }

    @PostMapping("/logout")
    @Operation(operationId = "authLogout", summary = "Revoke current or specified refresh session")
    @ApiResponse(responseCode = "200", description = "Logged out")
    public AuthDtos.MessageResponse logout(
            @Valid @RequestBody(required = false) AuthDtos.LogoutRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String refresh = request != null
                        && request.refreshToken() != null
                        && !request.refreshToken().isBlank()
                ? request.refreshToken()
                : AuthCookies.readRefreshCookie(httpRequest, authProperties);
        AuthDtos.MessageResponse response = authService.logout(refresh);
        AuthCookies.clearRefreshCookie(httpResponse, authProperties);
        return response;
    }

    @PostMapping("/oauth/{provider}/start")
    @Operation(operationId = "authOauthStart", summary = "Start OAuth (VK ID / Yandex ID) with PKCE")
    @ApiResponse(
            responseCode = "200",
            description = "Authorization URL",
            content = @Content(schema = @Schema(implementation = AuthDtos.OAuthStartResponse.class)))
    public AuthDtos.OAuthStartResponse oauthStart(
            @Parameter(description = "OAuth provider") @PathVariable ApiEnums.AuthProvider provider) {
        return authService.oauthStart(provider);
    }

    @PostMapping("/oauth/{provider}/callback")
    @Operation(operationId = "authOauthCallback", summary = "Complete OAuth callback and issue Posts tokens")
    @ApiResponse(
            responseCode = "200",
            description = "Tokens issued",
            content = @Content(schema = @Schema(implementation = AuthDtos.TokenResponse.class)))
    public AuthDtos.TokenResponse oauthCallback(
            @PathVariable ApiEnums.AuthProvider provider,
            @RequestParam String code,
            @RequestParam String state,
            @Parameter(description = "Device id returned by VK ID on the redirect; required for provider vk")
                    @RequestParam(name = "device_id", required = false)
                    String deviceId,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        TokenPair pair =
                authService.oauthCallback(provider, code, state, deviceId, ClientRequestContexts.from(httpRequest));
        AuthCookies.writeRefreshCookie(
                httpResponse,
                authProperties,
                pair.refreshToken(),
                jwtProperties.getRefreshTokenTtl().toSeconds());
        return toTokenResponse(pair);
    }

    @PostMapping("/oauth/link")
    @SecurityRequirements(value = {@SecurityRequirement(name = "bearer-jwt")})
    @Operation(
            operationId = "authOauthLink",
            summary = "Confirm linking an OAuth identity to the authenticated account",
            description = "Completes the oauth_link_required challenge from OAuth callback (US-1.2 / TC-AUTH-07).")
    @ApiResponse(responseCode = "200", description = "Identity linked")
    public AuthDtos.MessageResponse oauthLink(@Valid @RequestBody AuthDtos.OAuthLinkRequest request) {
        return authService.oauthLink(
                request, SecurityAuthSupport.requirePrincipal().userId());
    }

    @PostMapping("/2fa/setup")
    @SecurityRequirements(value = {@SecurityRequirement(name = "bearer-jwt")})
    @Operation(operationId = "auth2faSetup", summary = "Begin TOTP enrollment; returns otpauth URL and backup codes")
    @ApiResponse(
            responseCode = "200",
            description = "Setup payload",
            content = @Content(schema = @Schema(implementation = AuthDtos.TwoFactorSetupResponse.class)))
    public AuthDtos.TwoFactorSetupResponse twoFactorSetup() {
        return authService.twoFactorSetup(SecurityAuthSupport.requirePrincipal().userId());
    }

    @PostMapping("/2fa/confirm")
    @SecurityRequirements(value = {@SecurityRequirement(name = "bearer-jwt")})
    @Operation(operationId = "auth2faConfirm", summary = "Confirm TOTP enrollment with a code from the authenticator")
    @ApiResponse(responseCode = "200", description = "2FA enabled")
    public AuthDtos.MessageResponse twoFactorConfirm(@Valid @RequestBody AuthDtos.TwoFactorConfirmRequest request) {
        return authService.twoFactorConfirm(
                SecurityAuthSupport.requirePrincipal().userId(), request);
    }

    @PostMapping("/2fa/disable")
    @SecurityRequirements(value = {@SecurityRequirement(name = "bearer-jwt")})
    @Operation(operationId = "auth2faDisable", summary = "Disable TOTP after password + code verification")
    @ApiResponse(responseCode = "200", description = "2FA disabled")
    public AuthDtos.MessageResponse twoFactorDisable(@Valid @RequestBody AuthDtos.TwoFactorDisableRequest request) {
        return authService.twoFactorDisable(
                SecurityAuthSupport.requirePrincipal().userId(), request);
    }

    @PostMapping("/password/forgot")
    @Operation(
            operationId = "authPasswordForgot",
            summary = "Request password reset email",
            description = "Always returns the same shape to avoid email enumeration (US-1.4).")
    @ApiResponse(responseCode = "200", description = "Accepted")
    public AuthDtos.MessageResponse passwordForgot(@Valid @RequestBody AuthDtos.PasswordForgotRequest request) {
        return authService.passwordForgot(request);
    }

    @PostMapping("/password/reset")
    @Operation(operationId = "authPasswordReset", summary = "Reset password using email token")
    @ApiResponse(responseCode = "200", description = "Password updated")
    public AuthDtos.MessageResponse passwordReset(@Valid @RequestBody AuthDtos.PasswordResetRequest request) {
        return authService.passwordReset(request);
    }

    private static AuthDtos.TokenResponse toTokenResponse(TokenPair pair) {
        return new AuthDtos.TokenResponse(pair.accessToken(), "Bearer", pair.expiresIn(), pair.refreshToken());
    }
}
