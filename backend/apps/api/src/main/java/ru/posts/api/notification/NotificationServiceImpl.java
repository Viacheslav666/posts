package ru.posts.api.notification;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.api.web.dto.auth.AuthDtos;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.api.web.dto.notification.NotificationDtos;
import ru.posts.api.web.support.CursorPages;
import ru.posts.api.workspace.WorkspaceAccessService;
import ru.posts.common.uuid.UuidV7;
import ru.posts.persistence.entity.NotificationEntity;
import ru.posts.persistence.entity.NotificationSettingsEntity;
import ru.posts.persistence.entity.NotificationTelegramLinkEntity;
import ru.posts.persistence.entity.NotificationUnsubscribeTokenEntity;
import ru.posts.persistence.entity.NotificationWebPushSubscriptionEntity;
import ru.posts.persistence.repository.NotificationRepository;
import ru.posts.persistence.repository.NotificationSettingsRepository;
import ru.posts.persistence.repository.NotificationTelegramLinkRepository;
import ru.posts.persistence.repository.NotificationUnsubscribeTokenRepository;
import ru.posts.persistence.repository.NotificationWebPushSubscriptionRepository;
import ru.posts.security.crypto.SecureTokens;
import ru.posts.security.crypto.TokenHasher;

@Service
@Profile("!openapi")
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private static final String ACTION = "notifications.manage_self";
    private static final String META_MUTED = "mutedTypes";
    private static final String META_EMAIL = "emailEnabled";
    private static final String META_TELEGRAM = "telegramBotEnabled";
    private static final String META_WEB_PUSH = "webPushEnabled";
    private static final String META_IN_APP = "inAppEnabled";
    private static final String META_BY_TYPE = "byType";
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final WorkspaceAccessService access;
    private final NotificationRepository notificationRepository;
    private final NotificationSettingsRepository settingsRepository;
    private final NotificationTelegramLinkRepository telegramLinkRepository;
    private final NotificationWebPushSubscriptionRepository webPushRepository;
    private final NotificationUnsubscribeTokenRepository unsubscribeTokenRepository;
    private final NotificationProperties properties;

    public NotificationServiceImpl(
            WorkspaceAccessService access,
            NotificationRepository notificationRepository,
            NotificationSettingsRepository settingsRepository,
            NotificationTelegramLinkRepository telegramLinkRepository,
            NotificationWebPushSubscriptionRepository webPushRepository,
            NotificationUnsubscribeTokenRepository unsubscribeTokenRepository,
            NotificationProperties properties) {
        this.access = access;
        this.notificationRepository = notificationRepository;
        this.settingsRepository = settingsRepository;
        this.telegramLinkRepository = telegramLinkRepository;
        this.webPushRepository = webPushRepository;
        this.unsubscribeTokenRepository = unsubscribeTokenRepository;
        this.properties = properties;
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage.NotificationPage list(UUID userId, UUID workspaceId, Boolean unreadOnly, CursorPage.Params page) {
        access.requirePermission(workspaceId, userId, ACTION);
        boolean unread = Boolean.TRUE.equals(unreadOnly);
        List<NotificationEntity> rows = unread
                ? notificationRepository.findByUserIdAndWorkspaceIdAndReadAtIsNullOrderByCreatedAtDesc(
                        userId, workspaceId)
                : notificationRepository.findByUserIdAndWorkspaceIdOrderByCreatedAtDesc(userId, workspaceId);
        List<NotificationEntity> ordered = rows.stream()
                .sorted(Comparator.comparing(NotificationEntity::getId).reversed())
                .toList();
        CursorPages.Slice<NotificationEntity> slice =
                CursorPages.slice(ordered, page.cursor(), page.limitOrDefault(), NotificationEntity::getId);
        return new CursorPage.NotificationPage(
                slice.items().stream().map(this::toNotification).toList(), slice.nextCursor(), slice.hasMore());
    }

    @Override
    public AuthDtos.MessageResponse markRead(
            UUID userId, UUID workspaceId, NotificationDtos.MarkNotificationsReadRequest request) {
        access.requirePermission(workspaceId, userId, ACTION);
        List<UUID> ids = request == null || request.ids() == null ? List.of() : request.ids();
        boolean idsEmpty = ids.isEmpty();
        Collection<UUID> idParam = idsEmpty ? List.of(UuidV7.generate()) : ids;
        notificationRepository.markRead(userId, workspaceId, idParam, idsEmpty);
        return new AuthDtos.MessageResponse("ok");
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationDtos.NotificationSettings getSettings(UUID userId, UUID workspaceId) {
        access.requirePermission(workspaceId, userId, ACTION);
        return toSettingsDto(userId, loadOrDefaultSettings(userId));
    }

    @Override
    public NotificationDtos.NotificationSettings updateSettings(
            UUID userId, UUID workspaceId, NotificationDtos.UpdateNotificationSettingsRequest request) {
        access.requirePermission(workspaceId, userId, ACTION);
        NotificationSettingsEntity settings = loadOrDefaultSettings(userId);
        Map<String, Object> prefs = new LinkedHashMap<>(settings.getPreferences());
        Map<String, Object> byType = new LinkedHashMap<>(readByType(prefs));

        if (request.preferences() != null) {
            for (Map.Entry<String, NotificationDtos.ChannelPreference> entry :
                    request.preferences().entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                    continue;
                }
                byType.put(entry.getKey(), channelPrefToMap(entry.getValue()));
            }
        }
        prefs.put(META_BY_TYPE, byType);

        if (request.mutedTypes() != null) {
            prefs.put(META_MUTED, List.copyOf(request.mutedTypes()));
        }
        if (request.emailEnabled() != null) {
            prefs.put(META_EMAIL, request.emailEnabled());
        }
        if (request.telegramBotEnabled() != null) {
            prefs.put(META_TELEGRAM, request.telegramBotEnabled());
        }
        if (request.webPushEnabled() != null) {
            prefs.put(META_WEB_PUSH, request.webPushEnabled());
        }
        if (request.inAppEnabled() != null) {
            prefs.put(META_IN_APP, request.inAppEnabled());
        }
        settings.setPreferences(prefs);

        if (request.quietHours() != null) {
            NotificationDtos.QuietHours qh = request.quietHours();
            settings.setQuietHoursEnabled(qh.enabled());
            if (qh.timezone() != null && !qh.timezone().isBlank()) {
                settings.setTimezone(qh.timezone().trim());
            }
            if (qh.start() != null && !qh.start().isBlank()) {
                settings.setQuietHoursStart(parseTime(qh.start()));
            }
            if (qh.end() != null && !qh.end().isBlank()) {
                settings.setQuietHoursEnd(parseTime(qh.end()));
            }
        }
        settings.setUpdatedAt(Instant.now());
        settingsRepository.save(settings);
        return toSettingsDto(userId, settings);
    }

    @Override
    public NotificationDtos.TelegramLinkStartResponse startTelegramLink(UUID userId, UUID workspaceId) {
        access.requirePermission(workspaceId, userId, ACTION);
        String rawCode = SecureTokens.urlSafe(24);
        Instant expiresAt = Instant.now().plus(properties.telegramLinkCodeTtlMinutes(), ChronoUnit.MINUTES);
        NotificationTelegramLinkEntity link = telegramLinkRepository
                .findById(userId)
                .orElseGet(() -> {
                    NotificationTelegramLinkEntity created = new NotificationTelegramLinkEntity();
                    created.setUserId(userId);
                    created.setCreatedAt(Instant.now());
                    return created;
                });
        link.setLinkCodeHash(TokenHasher.sha256Hex(rawCode));
        link.setLinkCodeExpiresAt(expiresAt);
        link.setUpdatedAt(Instant.now());
        telegramLinkRepository.save(link);
        String bot = properties.telegramBotUsername();
        String deepLink = "https://t.me/" + bot + "?start=" + rawCode;
        return new NotificationDtos.TelegramLinkStartResponse(deepLink, expiresAt, bot);
    }

    @Override
    public AuthDtos.MessageResponse unlinkTelegram(UUID userId, UUID workspaceId) {
        access.requirePermission(workspaceId, userId, ACTION);
        telegramLinkRepository.findById(userId).ifPresent(link -> {
            link.setChatId(null);
            link.setUsername(null);
            link.setLinkedAt(null);
            link.setLinkCodeHash(null);
            link.setLinkCodeExpiresAt(null);
            link.setUpdatedAt(Instant.now());
            telegramLinkRepository.save(link);
        });
        return new AuthDtos.MessageResponse("telegram_unlinked");
    }

    @Override
    public AuthDtos.MessageResponse subscribeWebPush(
            UUID userId, UUID workspaceId, NotificationDtos.WebPushSubscribeRequest request) {
        access.requirePermission(workspaceId, userId, ACTION);
        NotificationWebPushSubscriptionEntity row = webPushRepository
                .findByUserIdAndEndpoint(userId, request.endpoint())
                .orElseGet(() -> {
                    NotificationWebPushSubscriptionEntity created = new NotificationWebPushSubscriptionEntity();
                    created.setId(UuidV7.generate());
                    created.setUserId(userId);
                    created.setEndpoint(request.endpoint());
                    created.setCreatedAt(Instant.now());
                    return created;
                });
        row.setP256dh(request.p256dh());
        row.setAuth(request.auth());
        row.setUserAgent(request.userAgent());
        row.setLastUsedAt(Instant.now());
        webPushRepository.save(row);
        return new AuthDtos.MessageResponse("web_push_subscribed");
    }

    @Override
    public AuthDtos.MessageResponse unsubscribeWebPush(UUID userId, UUID workspaceId, String endpoint) {
        access.requirePermission(workspaceId, userId, ACTION);
        if (endpoint == null || endpoint.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "endpoint is required");
        }
        webPushRepository.deleteByUserIdAndEndpoint(userId, endpoint);
        return new AuthDtos.MessageResponse("web_push_unsubscribed");
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationDtos.VapidPublicKeyResponse vapidPublicKey(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        return new NotificationDtos.VapidPublicKeyResponse(properties.vapidPublicKey());
    }

    @Override
    public NotificationDtos.UnsubscribeResponse unsubscribeByToken(String token) {
        if (token == null || token.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "token is required");
        }
        NotificationUnsubscribeTokenEntity row = unsubscribeTokenRepository
                .findByTokenHash(TokenHasher.sha256Hex(token))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Token not found"));
        if (row.getUsedAt() != null) {
            return new NotificationDtos.UnsubscribeResponse("already_unsubscribed");
        }
        if (row.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.CONFLICT, ProblemCodes.CONFLICT, "Token expired");
        }
        NotificationSettingsEntity settings = loadOrDefaultSettings(row.getUserId());
        if (row.getEventType() == null || row.getEventType().isBlank()) {
            settings.setEmailUnsubscribedAll(true);
            Map<String, Object> prefs = new LinkedHashMap<>(settings.getPreferences());
            prefs.put(META_EMAIL, false);
            settings.setPreferences(prefs);
        } else {
            Map<String, Object> prefs = new LinkedHashMap<>(settings.getPreferences());
            Map<String, Object> byType = new LinkedHashMap<>(readByType(prefs));
            Map<String, Object> channel = new LinkedHashMap<>(readChannelMap(byType.get(row.getEventType())));
            channel.put("email", false);
            byType.put(row.getEventType(), channel);
            prefs.put(META_BY_TYPE, byType);
            @SuppressWarnings("unchecked")
            List<String> muted = prefs.get(META_MUTED) instanceof List<?> list
                    ? new ArrayList<>((List<String>) list)
                    : new ArrayList<>();
            if (!muted.contains(row.getEventType())) {
                muted.add(row.getEventType());
            }
            prefs.put(META_MUTED, muted);
            settings.setPreferences(prefs);
        }
        settings.setUpdatedAt(Instant.now());
        settingsRepository.save(settings);
        row.setUsedAt(Instant.now());
        unsubscribeTokenRepository.save(row);
        return new NotificationDtos.UnsubscribeResponse("unsubscribed");
    }

    @Override
    public String createUnsubscribeToken(UUID userId, String eventType) {
        String raw = SecureTokens.urlSafe(32);
        NotificationUnsubscribeTokenEntity token = new NotificationUnsubscribeTokenEntity();
        token.setId(UuidV7.generate());
        token.setUserId(userId);
        token.setTokenHash(TokenHasher.sha256Hex(raw));
        token.setEventType(eventType);
        token.setCreatedAt(Instant.now());
        token.setExpiresAt(Instant.now().plus(properties.unsubscribeTokenTtlDays(), ChronoUnit.DAYS));
        unsubscribeTokenRepository.save(token);
        return raw;
    }

    @Override
    public Map<String, Object> handleTelegramNotifyWebhook(JsonNode body, String secretHeader) {
        String expected = properties.telegramWebhookSecret();
        if (expected == null || expected.isBlank() || secretHeader == null || !expected.equals(secretHeader)) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHORIZED, "Invalid Telegram webhook secret");
        }
        JsonNode message = body == null ? null : body.path("message");
        if (message == null || message.isMissingNode()) {
            return Map.of("ok", true);
        }
        String text = message.path("text").asText("").trim();
        if (!text.startsWith("/start")) {
            return Map.of("ok", true);
        }
        String code = text.length() > 6 ? text.substring(6).trim() : "";
        if (code.isBlank()) {
            return Map.of("ok", true, "linked", false);
        }
        long chatId = message.path("chat").path("id").asLong(0L);
        if (chatId == 0L) {
            return Map.of("ok", true, "linked", false);
        }
        String username = message.path("from").path("username").asText(null);
        Optional<NotificationTelegramLinkEntity> match =
                telegramLinkRepository.findByLinkCodeHash(TokenHasher.sha256Hex(code));
        if (match.isEmpty()) {
            return Map.of("ok", true, "linked", false);
        }
        NotificationTelegramLinkEntity link = match.get();
        if (link.getLinkCodeExpiresAt() == null || link.getLinkCodeExpiresAt().isBefore(Instant.now())) {
            return Map.of("ok", true, "linked", false, "reason", "expired");
        }
        telegramLinkRepository.findByChatId(chatId).ifPresent(other -> {
            if (!other.getUserId().equals(link.getUserId())) {
                other.setChatId(null);
                other.setUsername(null);
                other.setLinkedAt(null);
                other.setUpdatedAt(Instant.now());
                telegramLinkRepository.save(other);
            }
        });
        Instant now = Instant.now();
        link.setChatId(chatId);
        link.setUsername(username);
        link.setLinkedAt(now);
        link.setLinkCodeHash(null);
        link.setLinkCodeExpiresAt(null);
        link.setUpdatedAt(now);
        telegramLinkRepository.save(link);
        return Map.of("ok", true, "linked", true);
    }

    private NotificationDtos.Notification toNotification(NotificationEntity entity) {
        return new NotificationDtos.Notification(
                entity.getId(),
                entity.getType(),
                entity.getPayload() == null ? Map.of() : entity.getPayload(),
                entity.getReadAt(),
                entity.getCreatedAt(),
                entity.getWorkspaceId());
    }

    private NotificationSettingsEntity loadOrDefaultSettings(UUID userId) {
        return settingsRepository.findById(userId).orElseGet(() -> {
            NotificationSettingsEntity created = new NotificationSettingsEntity();
            created.setUserId(userId);
            created.setTimezone("Europe/Moscow");
            created.setQuietHoursEnabled(false);
            created.setPreferences(defaultPreferences());
            created.setEmailUnsubscribedAll(false);
            created.setUpdatedAt(Instant.now());
            return created;
        });
    }

    private Map<String, Object> defaultPreferences() {
        Map<String, Object> prefs = new LinkedHashMap<>();
        prefs.put(META_BY_TYPE, Map.of());
        prefs.put(META_MUTED, List.of());
        prefs.put(META_EMAIL, true);
        prefs.put(META_TELEGRAM, true);
        prefs.put(META_WEB_PUSH, true);
        prefs.put(META_IN_APP, true);
        return prefs;
    }

    private NotificationDtos.NotificationSettings toSettingsDto(UUID userId, NotificationSettingsEntity settings) {
        Map<String, Object> prefs = settings.getPreferences() == null ? Map.of() : settings.getPreferences();
        Map<String, NotificationDtos.ChannelPreference> byType = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : readByType(prefs).entrySet()) {
            byType.put(entry.getKey(), toChannelPreference(entry.getValue()));
        }
        List<String> muted = readStringList(prefs.get(META_MUTED));
        boolean emailEnabled = !settings.isEmailUnsubscribedAll() && readBool(prefs.get(META_EMAIL), true);
        boolean telegramEnabled = readBool(prefs.get(META_TELEGRAM), true);
        boolean webPushEnabled = readBool(prefs.get(META_WEB_PUSH), true);
        boolean inAppEnabled = readBool(prefs.get(META_IN_APP), true);

        String start = settings.getQuietHoursStart() == null
                ? null
                : settings.getQuietHoursStart().format(HH_MM);
        String end = settings.getQuietHoursEnd() == null
                ? null
                : settings.getQuietHoursEnd().format(HH_MM);
        NotificationDtos.QuietHours quietHours =
                new NotificationDtos.QuietHours(settings.isQuietHoursEnabled(), start, end, settings.getTimezone());

        NotificationTelegramLinkEntity link =
                telegramLinkRepository.findById(userId).orElse(null);
        boolean linked = link != null && link.isLinked();
        String bot = properties.telegramBotUsername();
        String deepLink = linked ? null : "https://t.me/" + bot;
        NotificationDtos.TelegramLinkStatus telegram = new NotificationDtos.TelegramLinkStatus(
                linked,
                bot,
                deepLink,
                link == null ? null : link.getLinkedAt(),
                link == null ? null : link.getUsername());

        int pushCount = webPushRepository.findByUserId(userId).size();
        return new NotificationDtos.NotificationSettings(
                byType,
                quietHours,
                emailEnabled,
                telegramEnabled,
                webPushEnabled,
                inAppEnabled,
                muted,
                telegram,
                properties.vapidPublicKey(),
                pushCount);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readByType(Map<String, Object> prefs) {
        Object raw = prefs.get(META_BY_TYPE);
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null) {
                    out.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
            return out;
        }
        // Backward-compat: treat top-level non-meta keys as type prefs.
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : prefs.entrySet()) {
            if (isMetaKey(e.getKey())) {
                continue;
            }
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    private static boolean isMetaKey(String key) {
        return META_BY_TYPE.equals(key)
                || META_MUTED.equals(key)
                || META_EMAIL.equals(key)
                || META_TELEGRAM.equals(key)
                || META_WEB_PUSH.equals(key)
                || META_IN_APP.equals(key);
    }

    private static Map<String, Object> channelPrefToMap(NotificationDtos.ChannelPreference pref) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("email", pref.email());
        map.put("telegram", pref.telegram());
        map.put("webPush", pref.webPush());
        map.put("inApp", pref.inApp());
        return map;
    }

    private static NotificationDtos.ChannelPreference toChannelPreference(Object raw) {
        Map<String, Object> map = readChannelMap(raw);
        return new NotificationDtos.ChannelPreference(
                readBool(map.get("email"), true),
                readBool(map.get("telegram"), true),
                readBool(map.get("webPush"), true),
                readBool(map.get("inApp"), true));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readChannelMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> out = new HashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null) {
                    out.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
            return out;
        }
        return Map.of();
    }

    private static boolean readBool(Object value, boolean defaultValue) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private static List<String> readStringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                out.add(String.valueOf(item));
            }
        }
        return List.copyOf(out);
    }

    private static LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value.trim(), HH_MM);
        } catch (DateTimeParseException ex) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ProblemCodes.VALIDATION_FAILED,
                    "quietHours time must be HH:mm, got: " + value);
        }
    }
}
