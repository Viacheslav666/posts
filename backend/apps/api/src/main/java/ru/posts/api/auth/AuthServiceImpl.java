package ru.posts.api.auth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.api.admin.FeatureFlagResolver;
import ru.posts.api.admin.ImpersonationGuard;
import ru.posts.api.auth.crypto.SecretBox;
import ru.posts.api.auth.mail.AuthMailSender;
import ru.posts.api.auth.oauth.OAuthCodeExchange;
import ru.posts.api.auth.oauth.OAuthProviderClient;
import ru.posts.api.auth.oauth.OAuthUserProfile;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.common.ApiEnums;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.user.UserDtos;
import ru.posts.common.uuid.UuidV7;
import ru.posts.persistence.entity.AuthTokenEntity;
import ru.posts.persistence.entity.OauthLinkChallengeEntity;
import ru.posts.persistence.entity.OauthStateEntity;
import ru.posts.persistence.entity.SessionEntity;
import ru.posts.persistence.entity.TotpBackupCodeEntity;
import ru.posts.persistence.entity.UserEntity;
import ru.posts.persistence.entity.UserIdentityEntity;
import ru.posts.persistence.entity.WorkspaceEntity;
import ru.posts.persistence.entity.WorkspaceMemberEntity;
import ru.posts.persistence.repository.AnnouncementRepository;
import ru.posts.persistence.repository.AuthTokenRepository;
import ru.posts.persistence.repository.OauthLinkChallengeRepository;
import ru.posts.persistence.repository.OauthStateRepository;
import ru.posts.persistence.repository.PlanRepository;
import ru.posts.persistence.repository.SessionRepository;
import ru.posts.persistence.repository.TotpBackupCodeRepository;
import ru.posts.persistence.repository.UserIdentityRepository;
import ru.posts.persistence.repository.UserRepository;
import ru.posts.persistence.repository.WorkspaceMemberRepository;
import ru.posts.persistence.repository.WorkspaceRepository;
import ru.posts.ratelimit.login.LoginRateLimiter;
import ru.posts.security.auth.PostsPrincipal;
import ru.posts.security.crypto.SecureTokens;
import ru.posts.security.crypto.TokenHasher;
import ru.posts.security.jwt.JwtProperties;
import ru.posts.security.jwt.JwtService;
import ru.posts.security.password.PasswordService;
import ru.posts.security.password.WeakPasswordException;
import ru.posts.security.totp.TotpService;

@Service
@Profile("!openapi")
@Transactional
public class AuthServiceImpl implements AuthService {

    private static final String PURPOSE_EMAIL_VERIFY = "email_verify";
    private static final String PURPOSE_PASSWORD_RESET = "password_reset";
    private static final String MSG_VERIFICATION_SENT = "verification_email_sent";
    private static final String MSG_PASSWORD_RESET_SENT = "password_reset_email_sent";

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final SessionRepository sessionRepository;
    private final AuthTokenRepository authTokenRepository;
    private final TotpBackupCodeRepository totpBackupCodeRepository;
    private final OauthStateRepository oauthStateRepository;
    private final OauthLinkChallengeRepository oauthLinkChallengeRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final PlanRepository planRepository;
    private final PasswordService passwordService;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final TotpService totpService;
    private final SecretBox secretBox;
    private final AuthMailSender mailSender;
    private final AuthProperties authProperties;
    private final LoginRateLimiter loginRateLimiter;
    private final OAuthProviderClient oauthProviderClient;
    private final AuthTxHelper authTxHelper;
    private final ImpersonationGuard impersonationGuard;
    private final FeatureFlagResolver featureFlagResolver;
    private final AnnouncementRepository announcementRepository;

    public AuthServiceImpl(
            UserRepository userRepository,
            UserIdentityRepository userIdentityRepository,
            SessionRepository sessionRepository,
            AuthTokenRepository authTokenRepository,
            TotpBackupCodeRepository totpBackupCodeRepository,
            OauthStateRepository oauthStateRepository,
            OauthLinkChallengeRepository oauthLinkChallengeRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            PlanRepository planRepository,
            PasswordService passwordService,
            JwtService jwtService,
            JwtProperties jwtProperties,
            TotpService totpService,
            SecretBox secretBox,
            AuthMailSender mailSender,
            AuthProperties authProperties,
            LoginRateLimiter loginRateLimiter,
            OAuthProviderClient oauthProviderClient,
            AuthTxHelper authTxHelper,
            ImpersonationGuard impersonationGuard,
            FeatureFlagResolver featureFlagResolver,
            AnnouncementRepository announcementRepository) {
        this.userRepository = userRepository;
        this.userIdentityRepository = userIdentityRepository;
        this.sessionRepository = sessionRepository;
        this.authTokenRepository = authTokenRepository;
        this.totpBackupCodeRepository = totpBackupCodeRepository;
        this.oauthStateRepository = oauthStateRepository;
        this.oauthLinkChallengeRepository = oauthLinkChallengeRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.planRepository = planRepository;
        this.passwordService = passwordService;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.totpService = totpService;
        this.secretBox = secretBox;
        this.mailSender = mailSender;
        this.authProperties = authProperties;
        this.loginRateLimiter = loginRateLimiter;
        this.oauthProviderClient = oauthProviderClient;
        this.authTxHelper = authTxHelper;
        this.impersonationGuard = impersonationGuard;
        this.featureFlagResolver = featureFlagResolver;
        this.announcementRepository = announcementRepository;
    }

    @Override
    public AuthDtos.MessageResponse register(AuthDtos.RegisterRequest request) {
        String email = normalizeEmail(request.email());
        try {
            passwordService.validateStrength(request.password());
        } catch (WeakPasswordException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, ex.getMessage());
        }

        Optional<UserEntity> existing = userRepository.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            return new AuthDtos.MessageResponse(MSG_VERIFICATION_SENT);
        }

        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setPasswordHash(passwordService.hash(request.password()));
        user.setName(request.name().trim());
        user.setTimezone(blankToDefault(request.timezone(), "Europe/Moscow"));
        user.setLocale(blankToDefault(request.locale(), "ru"));
        user = userRepository.save(user);
        createPersonalWorkspace(user);
        issueEmailVerification(user);
        return new AuthDtos.MessageResponse(MSG_VERIFICATION_SENT);
    }

    @Override
    public AuthDtos.MessageResponse verifyEmail(AuthDtos.EmailVerifyRequest request) {
        AuthTokenEntity token = requireValidToken(request.token(), PURPOSE_EMAIL_VERIFY);
        UserEntity user = userRepository
                .findById(token.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "User not found"));
        user.setEmailVerifiedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        token.setUsedAt(Instant.now());
        return new AuthDtos.MessageResponse("email_verified");
    }

    @Override
    public TokenPair login(AuthDtos.LoginRequest request, ClientRequestContext ctx) {
        String email = normalizeEmail(request.email());
        String rateKey = rateKey(ctx.ip(), email);
        if (!loginRateLimiter.tryConsume(rateKey)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, ProblemCodes.RATE_LIMITED, "Too many login attempts");
        }

        UserEntity user = userRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow(() ->
                        new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, "Invalid credentials"));
        if (user.getDeletedAt() != null || !passwordService.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, "Invalid credentials");
        }
        if (user.getEmailVerifiedAt() == null) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, ProblemCodes.EMAIL_NOT_VERIFIED, "Email address is not verified");
        }
        assertSecondFactor(user, request.totpCode());
        loginRateLimiter.reset(rateKey);
        return issueSession(user, ctx);
    }

    @Override
    public TokenPair refresh(String refreshToken, ClientRequestContext ctx) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, "Refresh token required");
        }
        String hash = TokenHasher.sha256Hex(refreshToken);
        Optional<SessionEntity> active = sessionRepository.findByRefreshTokenHashAndRevokedAtIsNull(hash);
        if (active.isPresent()) {
            SessionEntity session = active.get();
            if (session.getCreatedAt().plus(jwtProperties.getRefreshTokenTtl()).isBefore(Instant.now())) {
                session.setRevokedAt(Instant.now());
                throw new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, "Refresh token expired");
            }
            UserEntity user = userRepository
                    .findById(session.getUserId())
                    .orElseThrow(
                            () -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "User not found"));
            session.setRevokedAt(Instant.now());
            return rotateSession(user, session.getFamilyId(), ctx);
        }

        Optional<SessionEntity> reused = sessionRepository.findByRefreshTokenHash(hash);
        if (reused.isPresent()) {
            SessionEntity session = reused.get();
            authTxHelper.revokeFamily(sessionRepository::revokeFamily, session.getFamilyId(), Instant.now());
            throw new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, "Refresh token reuse detected");
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, "Invalid refresh token");
    }

    @Override
    public AuthDtos.MessageResponse logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return new AuthDtos.MessageResponse("logged_out");
        }
        sessionRepository
                .findByRefreshTokenHashAndRevokedAtIsNull(TokenHasher.sha256Hex(refreshToken))
                .ifPresent(session -> session.setRevokedAt(Instant.now()));
        return new AuthDtos.MessageResponse("logged_out");
    }

    @Override
    public AuthDtos.OAuthStartResponse oauthStart(ApiEnums.AuthProvider provider) {
        if (provider != ApiEnums.AuthProvider.vk && provider != ApiEnums.AuthProvider.yandex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "Unsupported provider");
        }
        String key = provider.name();
        AuthProperties.OAuthProviderProperties cfg = authProperties.getOauth().get(key);
        if (cfg == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "OAuth provider not configured");
        }
        String state = SecureTokens.urlSafe(24);
        String codeVerifier = SecureTokens.urlSafe(64);
        String codeChallenge = pkceChallenge(codeVerifier);
        OauthStateEntity entity = new OauthStateEntity();
        entity.setProvider(key);
        entity.setState(state);
        entity.setCodeVerifier(codeVerifier);
        entity.setNonce(SecureTokens.urlSafe(16));
        entity.setExpiresAt(Instant.now().plus(authProperties.getOauthStateTtl()));
        oauthStateRepository.save(entity);

        String url = cfg.getAuthorizationUri()
                + "?response_type=code"
                + "&client_id="
                + enc(cfg.getClientId())
                + "&redirect_uri="
                + enc(cfg.getRedirectUri())
                + "&scope="
                + enc(cfg.getScopes())
                + "&state="
                + enc(state)
                + "&code_challenge="
                + enc(codeChallenge)
                + "&code_challenge_method=S256";
        return new AuthDtos.OAuthStartResponse(url, state);
    }

    @Override
    public TokenPair oauthCallback(
            ApiEnums.AuthProvider provider, String code, String state, String deviceId, ClientRequestContext ctx) {
        String providerKey = provider.name();
        OauthStateEntity oauthState = oauthStateRepository
                .findByState(state)
                .orElseThrow(() ->
                        new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, "Invalid OAuth state"));
        if (!providerKey.equals(oauthState.getProvider())
                || oauthState.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, "Invalid OAuth state");
        }
        oauthStateRepository.delete(oauthState);

        AuthProperties.OAuthProviderProperties cfg = authProperties.getOauth().get(providerKey);
        OAuthUserProfile profile = oauthProviderClient.exchange(
                providerKey,
                new OAuthCodeExchange(code, oauthState.getCodeVerifier(), cfg.getRedirectUri(), state, deviceId));
        if (profile.providerUserId() == null || profile.providerUserId().isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, "OAuth profile incomplete");
        }

        Optional<UserIdentityEntity> identity =
                userIdentityRepository.findByProviderAndProviderUserId(providerKey, profile.providerUserId());
        if (identity.isPresent()) {
            UserEntity user = userRepository
                    .findById(identity.get().getUserId())
                    .orElseThrow(
                            () -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "User not found"));
            return issueSession(user, ctx);
        }

        boolean hasEmail = profile.email() != null && !profile.email().isBlank();
        boolean emailTaken = false;
        if (hasEmail) {
            Optional<UserEntity> byEmail = userRepository.findByEmailIgnoreCase(normalizeEmail(profile.email()));
            emailTaken = byEmail.isPresent();
            if (byEmail.isPresent() && byEmail.get().getEmailVerifiedAt() != null) {
                String linkToken = SecureTokens.urlSafe(32);
                authTxHelper.runInNewTx(() -> {
                    OauthLinkChallengeEntity challenge = new OauthLinkChallengeEntity();
                    challenge.setUserId(byEmail.get().getId());
                    challenge.setProvider(providerKey);
                    challenge.setProviderUserId(profile.providerUserId());
                    challenge.setProfile(profile.raw() == null ? Map.of() : profile.raw());
                    challenge.setTokenHash(TokenHasher.sha256Hex(linkToken));
                    challenge.setExpiresAt(Instant.now().plus(authProperties.getOauthLinkTtl()));
                    oauthLinkChallengeRepository.save(challenge);
                });
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        ProblemCodes.OAUTH_LINK_REQUIRED,
                        "Account linking confirmation required",
                        Map.of("linkToken", linkToken, "provider", providerKey));
            }
        }

        // No email from the provider (VK may omit it), or it belongs to an unverified local account: register by
        // provider user id under a placeholder address instead of claiming someone else's email.
        boolean useProviderEmail = hasEmail && !emailTaken;
        UserEntity user = new UserEntity();
        String email = useProviderEmail
                ? normalizeEmail(profile.email())
                : providerKey + "+" + profile.providerUserId() + "@oauth.posts.local";
        user.setEmail(email);
        if (useProviderEmail && profile.emailVerified()) {
            user.setEmailVerifiedAt(Instant.now());
        }
        user.setName(profile.name() == null || profile.name().isBlank() ? "User" : profile.name());
        user.setTimezone("Europe/Moscow");
        user.setLocale("ru");
        user = userRepository.save(user);
        createPersonalWorkspace(user);
        saveIdentity(user.getId(), providerKey, profile);
        return issueSession(user, ctx);
    }

    @Override
    public AuthDtos.MessageResponse oauthLink(AuthDtos.OAuthLinkRequest request, UUID userId) {
        OauthLinkChallengeEntity challenge = oauthLinkChallengeRepository
                .findByTokenHashAndConsumedAtIsNull(TokenHasher.sha256Hex(request.linkToken()))
                .orElseThrow(() ->
                        new ApiException(HttpStatus.BAD_REQUEST, ProblemCodes.INVALID_TOKEN, "Invalid link token"));
        if (!challenge.getUserId().equals(userId) || challenge.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ProblemCodes.INVALID_TOKEN, "Invalid link token");
        }
        if (userIdentityRepository
                .findByProviderAndProviderUserId(challenge.getProvider(), challenge.getProviderUserId())
                .isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, ProblemCodes.CONFLICT, "Identity already linked");
        }
        UserIdentityEntity identity = new UserIdentityEntity();
        identity.setUserId(userId);
        identity.setProvider(challenge.getProvider());
        identity.setProviderUserId(challenge.getProviderUserId());
        identity.setProfile(challenge.getProfile());
        userIdentityRepository.save(identity);
        challenge.setConsumedAt(Instant.now());
        return new AuthDtos.MessageResponse("oauth_linked");
    }

    @Override
    public AuthDtos.TwoFactorSetupResponse twoFactorSetup(UUID userId) {
        UserEntity user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "User not found"));
        if (user.isTotpEnabled()) {
            throw new ApiException(HttpStatus.CONFLICT, ProblemCodes.CONFLICT, "2FA already enabled");
        }
        String secret = totpService.generateSecret();
        user.setTotpSecretEnc(secretBox.encrypt(secret));
        user.setUpdatedAt(Instant.now());
        totpBackupCodeRepository.deleteByUserId(userId);
        List<String> backupCodes = totpService.generateBackupCodes(10);
        for (String code : backupCodes) {
            TotpBackupCodeEntity entity = new TotpBackupCodeEntity();
            entity.setUserId(userId);
            entity.setCodeHash(TokenHasher.sha256Hex(code));
            totpBackupCodeRepository.save(entity);
        }
        String otpauth = totpService.otpauthUrl(authProperties.getTotpIssuer(), user.getEmail(), secret);
        return new AuthDtos.TwoFactorSetupResponse(otpauth, secret, backupCodes);
    }

    @Override
    public AuthDtos.MessageResponse twoFactorConfirm(UUID userId, AuthDtos.TwoFactorConfirmRequest request) {
        UserEntity user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "User not found"));
        if (user.getTotpSecretEnc() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "2FA setup not started");
        }
        String secret = secretBox.decrypt(user.getTotpSecretEnc());
        if (!totpService.verify(secret, request.code())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, "Invalid TOTP code");
        }
        user.setTotpEnabled(true);
        user.setUpdatedAt(Instant.now());
        return new AuthDtos.MessageResponse("2fa_enabled");
    }

    @Override
    public AuthDtos.MessageResponse twoFactorDisable(UUID userId, AuthDtos.TwoFactorDisableRequest request) {
        // US-1.3: self-service reset without support is unavailable.
        throw new ApiException(
                HttpStatus.FORBIDDEN, ProblemCodes.FORBIDDEN, "Self-service 2FA reset is unavailable; contact support");
    }

    @Override
    public AuthDtos.MessageResponse passwordForgot(AuthDtos.PasswordForgotRequest request) {
        String email = normalizeEmail(request.email());
        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            if (user.getEmailVerifiedAt() != null) {
                String raw = SecureTokens.urlSafe(32);
                AuthTokenEntity token = new AuthTokenEntity();
                token.setUserId(user.getId());
                token.setPurpose(PURPOSE_PASSWORD_RESET);
                token.setTokenHash(TokenHasher.sha256Hex(raw));
                token.setExpiresAt(Instant.now().plus(authProperties.getPasswordResetTtl()));
                authTokenRepository.save(token);
                String link = authProperties.getAppBaseUrl() + "/reset-password?token=" + raw;
                mailSender.send(
                        user.getEmail(),
                        "Сброс пароля Posts",
                        "Чтобы сбросить пароль, откройте ссылку (действует 1 час):\n" + link);
            }
        });
        return new AuthDtos.MessageResponse(MSG_PASSWORD_RESET_SENT);
    }

    @Override
    public AuthDtos.MessageResponse passwordReset(AuthDtos.PasswordResetRequest request) {
        try {
            passwordService.validateStrength(request.newPassword());
        } catch (WeakPasswordException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, ex.getMessage());
        }
        AuthTokenEntity token = requireValidToken(request.token(), PURPOSE_PASSWORD_RESET);
        UserEntity user = userRepository
                .findById(token.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "User not found"));
        user.setPasswordHash(passwordService.hash(request.newPassword()));
        user.setUpdatedAt(Instant.now());
        token.setUsedAt(Instant.now());
        sessionRepository.revokeAllForUser(user.getId(), Instant.now());
        return new AuthDtos.MessageResponse("password_updated");
    }

    @Override
    @Transactional(readOnly = true)
    public UserDtos.UserProfile me(PostsPrincipal principal) {
        UserEntity user = userRepository
                .findById(principal.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "User not found"));
        return toProfile(user, principal);
    }

    @Override
    public UserDtos.UserProfile updateMe(UUID userId, UserDtos.UpdateMeRequest request) {
        UserEntity user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "User not found"));
        if (request.name() != null && !request.name().isBlank()) {
            user.setName(request.name().trim());
        }
        if (request.timezone() != null && !request.timezone().isBlank()) {
            user.setTimezone(request.timezone());
        }
        if (request.locale() != null && !request.locale().isBlank()) {
            user.setLocale(request.locale());
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl());
        }
        user.setUpdatedAt(Instant.now());
        return toProfile(user, null);
    }

    @Override
    public AuthDtos.MessageResponse changePassword(
            UUID userId, UUID currentSessionId, UserDtos.UpdatePasswordRequest request) {
        impersonationGuard.forbidIfImpersonating("change_password");
        UserEntity user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "User not found"));
        if (!passwordService.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, "Invalid credentials");
        }
        try {
            passwordService.validateStrength(request.newPassword());
        } catch (WeakPasswordException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, ex.getMessage());
        }
        user.setPasswordHash(passwordService.hash(request.newPassword()));
        user.setUpdatedAt(Instant.now());
        if (currentSessionId == null) {
            sessionRepository.revokeAllForUser(userId, Instant.now());
        } else {
            sessionRepository.revokeOtherSessions(userId, currentSessionId, Instant.now());
        }
        return new AuthDtos.MessageResponse("password_changed");
    }

    @Override
    public AuthDtos.MessageResponse deleteMe(UUID userId) {
        impersonationGuard.forbidIfImpersonating("delete_account");
        UserEntity user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "User not found"));
        user.setDeletedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        sessionRepository.revokeAllForUser(userId, Instant.now());
        return new AuthDtos.MessageResponse("deletion_scheduled");
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage.SessionPage listSessions(UUID userId, UUID currentSessionId, CursorPage.Params page) {
        List<SessionEntity> sessions = sessionRepository.findByUserIdOrderByLastUsedAtDesc(userId);
        int limit = page.limitOrDefault();
        List<AuthDtos.SessionDto> items = new ArrayList<>();
        for (SessionEntity session : sessions) {
            if (items.size() >= limit) {
                break;
            }
            items.add(new AuthDtos.SessionDto(
                    session.getId(),
                    session.getUserAgent(),
                    session.getIp(),
                    session.getCreatedAt(),
                    session.getLastUsedAt(),
                    currentSessionId != null && currentSessionId.equals(session.getId()),
                    session.getRevokedAt()));
        }
        boolean hasMore = sessions.size() > items.size();
        String next =
                hasMore && !items.isEmpty() ? items.get(items.size() - 1).id().toString() : null;
        return new CursorPage.SessionPage(items, next, hasMore);
    }

    @Override
    public AuthDtos.MessageResponse revokeSession(UUID userId, UUID sessionId) {
        SessionEntity session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Session not found"));
        if (!session.getUserId().equals(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Session not found");
        }
        if (session.getRevokedAt() == null) {
            session.setRevokedAt(Instant.now());
        }
        return new AuthDtos.MessageResponse("session_revoked");
    }

    @Override
    public AuthDtos.MessageResponse revokeOtherSessions(UUID userId, UUID currentSessionId) {
        if (currentSessionId == null) {
            sessionRepository.revokeAllForUser(userId, Instant.now());
        } else {
            sessionRepository.revokeOtherSessions(userId, currentSessionId, Instant.now());
        }
        return new AuthDtos.MessageResponse("other_sessions_revoked");
    }

    private void assertSecondFactor(UserEntity user, String totpOrBackup) {
        if (!user.isTotpEnabled()) {
            return;
        }
        if (totpOrBackup == null || totpOrBackup.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.TOTP_REQUIRED, "TOTP code required");
        }
        String secret = secretBox.decrypt(user.getTotpSecretEnc());
        if (totpService.verify(secret, totpOrBackup)) {
            return;
        }
        String codeHash = TokenHasher.sha256Hex(totpOrBackup.trim());
        List<TotpBackupCodeEntity> codes = totpBackupCodeRepository.findByUserIdAndUsedAtIsNull(user.getId());
        for (TotpBackupCodeEntity code : codes) {
            if (code.getCodeHash().equals(codeHash)) {
                code.setUsedAt(Instant.now());
                return;
            }
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, "Invalid TOTP code");
    }

    private TokenPair issueSession(UserEntity user, ClientRequestContext ctx) {
        UUID familyId = UuidV7.generate();
        return rotateSession(user, familyId, ctx);
    }

    private TokenPair rotateSession(UserEntity user, UUID familyId, ClientRequestContext ctx) {
        String refresh = SecureTokens.urlSafe(48);
        SessionEntity session = new SessionEntity();
        session.setUserId(user.getId());
        session.setFamilyId(familyId);
        session.setRefreshTokenHash(TokenHasher.sha256Hex(refresh));
        session.setUserAgent(truncate(ctx.userAgent(), 512));
        session.setIp(normalizeIp(ctx.ip()));
        session = sessionRepository.save(session);
        JwtService.IssuedAccessToken access = jwtService.issueAccessToken(user.getId(), session.getId());
        session.setLastUsedAt(Instant.now());
        return new TokenPair(access.token(), access.expiresInSeconds(), refresh, session.getId());
    }

    private void createPersonalWorkspace(UserEntity user) {
        var freePlan = planRepository.findByCode("free").orElseThrow();
        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setName("Личное пространство");
        workspace.setOwnerUserId(user.getId());
        workspace.setPlanId(freePlan.getId());
        workspace.setBillingStatus("active");
        workspace.setTrialEndsAt(null);
        workspace = workspaceRepository.save(workspace);

        WorkspaceMemberEntity member = new WorkspaceMemberEntity();
        member.setWorkspaceId(workspace.getId());
        member.setUserId(user.getId());
        member.setRole("owner");
        workspaceMemberRepository.save(member);
    }

    private void issueEmailVerification(UserEntity user) {
        String raw = SecureTokens.urlSafe(32);
        AuthTokenEntity token = new AuthTokenEntity();
        token.setUserId(user.getId());
        token.setPurpose(PURPOSE_EMAIL_VERIFY);
        token.setTokenHash(TokenHasher.sha256Hex(raw));
        token.setExpiresAt(Instant.now().plus(authProperties.getEmailVerifyTtl()));
        authTokenRepository.save(token);
        String link = authProperties.getAppBaseUrl() + "/verify-email?token=" + raw;
        mailSender.send(
                user.getEmail(),
                "Подтверждение email Posts",
                "Подтвердите email, открыв ссылку (действует 24 часа):\n" + link);
    }

    private AuthTokenEntity requireValidToken(String raw, String purpose) {
        AuthTokenEntity token = authTokenRepository
                .findByTokenHashAndPurposeAndUsedAtIsNull(TokenHasher.sha256Hex(raw), purpose)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST, ProblemCodes.INVALID_TOKEN, "Invalid or expired token"));
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ProblemCodes.INVALID_TOKEN, "Invalid or expired token");
        }
        return token;
    }

    private void saveIdentity(UUID userId, String provider, OAuthUserProfile profile) {
        UserIdentityEntity identity = new UserIdentityEntity();
        identity.setUserId(userId);
        identity.setProvider(provider);
        identity.setProviderUserId(profile.providerUserId());
        identity.setProfile(profile.raw() == null ? Map.of() : profile.raw());
        userIdentityRepository.save(identity);
    }

    private UserDtos.UserProfile toProfile(UserEntity user, PostsPrincipal principal) {
        List<UserDtos.MembershipSummary> memberships = workspaceMemberRepository.findByUserId(user.getId()).stream()
                .map(member -> {
                    WorkspaceEntity workspace = workspaceRepository
                            .findById(member.getWorkspaceId())
                            .orElse(null);
                    return new UserDtos.MembershipSummary(
                            member.getWorkspaceId(),
                            workspace == null ? "Workspace" : workspace.getName(),
                            ApiEnums.MemberRole.valueOf(member.getRole()),
                            workspace == null
                                    ? ApiEnums.BillingStatus.trial
                                    : ApiEnums.BillingStatus.valueOf(workspace.getBillingStatus()));
                })
                .toList();
        UserDtos.ImpersonationBanner impersonation = null;
        if (principal != null && principal.impersonating()) {
            impersonation = new UserDtos.ImpersonationBanner(
                    true, principal.actorUserId(), principal.impersonationReason(), principal.expiresAt());
        }
        List<UserDtos.AnnouncementBanner> announcements = announcementRepository.findActiveAt(Instant.now()).stream()
                .map(a -> new UserDtos.AnnouncementBanner(
                        a.getId(), a.getTitle(), a.getBody(), a.getSeverity(), a.getEndsAt()))
                .toList();
        return new UserDtos.UserProfile(
                user.getId(),
                user.getEmail(),
                user.getEmailVerifiedAt() != null,
                user.getName(),
                user.getAvatarUrl(),
                user.getTimezone(),
                user.getLocale(),
                user.isTotpEnabled(),
                user.getCreatedAt(),
                memberships,
                user.isPlatformAdmin(),
                featureFlagResolver.globalFeatures(),
                impersonation,
                announcements);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static String rateKey(String ip, String email) {
        return (ip == null ? "unknown" : ip) + "|" + email;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String normalizeIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        // Strip port / IPv6 brackets for inet column friendliness in simple cases.
        String cleaned = ip;
        int comma = cleaned.indexOf(',');
        if (comma >= 0) {
            cleaned = cleaned.substring(0, comma).trim();
        }
        if (cleaned.startsWith("[") && cleaned.contains("]")) {
            cleaned = cleaned.substring(1, cleaned.indexOf(']'));
        } else if (cleaned.contains(":")
                && cleaned.chars().filter(ch -> ch == ':').count() == 1) {
            cleaned = cleaned.substring(0, cleaned.indexOf(':'));
        }
        return cleaned;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String pkceChallenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
