package ru.posts.publisher.channel;

import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import ru.posts.security.crypto.TokenCryptoService;
import ru.posts.social.api.AccessTokenKind;
import ru.posts.social.api.ChannelContext;
import ru.posts.social.api.ModerationTarget;
import ru.posts.social.api.Network;

/**
 * Resolves decrypted bot/access tokens and Telegram {@code thread_id} for publisher calls (ADR-0006).
 *
 * <p>Default {@link #context(UUID, UUID, String, Network)} prefers {@code channels.bot_token_enc}
 * (custom bot), then {@code social_accounts.access_token_enc}, then the configured fallback.
 *
 * <p>BE-33 moderation uses {@link #context(UUID, UUID, String, Network, AccessTokenKind)} so VK
 * user-token actions prefer the linked social account token.
 */
@Component
public class ChannelAccessResolver {

    private final JdbcClient jdbcClient;
    private final TokenCryptoService crypto;
    private final String fallbackToken;

    public ChannelAccessResolver(
            JdbcClient jdbcClient,
            TokenCryptoService crypto,
            @Value("${posts.publisher.fallback-bot-token:emulator-token}") String fallbackToken) {
        this.jdbcClient = jdbcClient;
        this.crypto = crypto;
        this.fallbackToken = fallbackToken == null || fallbackToken.isBlank() ? "emulator-token" : fallbackToken;
    }

    /** BOT preference (publish / scheduled_actions) — existing behavior. */
    public ChannelContext context(UUID workspaceId, UUID channelId, String externalId, Network network) {
        return context(workspaceId, channelId, externalId, network, AccessTokenKind.BOT);
    }

    /**
     * Resolves a token for the given {@link AccessTokenKind}:
     *
     * <ul>
     *   <li>{@link AccessTokenKind#USER} — prefer {@code social_accounts.access_token_enc}, else bot,
     *       else fallback
     *   <li>{@link AccessTokenKind#COMMUNITY} / {@link AccessTokenKind#BOT} — prefer {@code
     *       channels.bot_token_enc}, else social_accounts, else fallback
     * </ul>
     */
    public ChannelContext context(
            UUID workspaceId, UUID channelId, String externalId, Network network, AccessTokenKind kind) {
        AccessRow row = load(channelId);
        String token = unsealPreferring(row, kind == null ? AccessTokenKind.BOT : kind);
        String threadId =
                row == null || row.threadId() == null || row.threadId().isBlank() ? null : row.threadId();
        String resolvedExternal = externalId;
        if ((resolvedExternal == null || resolvedExternal.isBlank()) && row != null) {
            resolvedExternal = row.externalId();
        }
        return new ChannelContext(workspaceId, channelId, resolvedExternal, token, network, threadId);
    }

    /**
     * Maps a remote-action kind (+ optional target) to the credential preference for that network.
     *
     * <ul>
     *   <li>VK {@code publish_suggested}, {@code delete_post}, wall/photo/video/market comment
     *       delete/restore, ban/unban → {@link AccessTokenKind#USER}
     *   <li>VK board comments / {@code reply_comment} → USER preferred (falls back to community bot)
     *   <li>VK {@code notify_author} → {@link AccessTokenKind#COMMUNITY}
     *   <li>TG / Max → {@link AccessTokenKind#BOT}
     * </ul>
     */
    public static AccessTokenKind tokenKindFor(String network, String kind, ModerationTarget target) {
        String net = network == null ? "" : network.trim().toLowerCase(Locale.ROOT);
        if (!"vk".equals(net)) {
            return AccessTokenKind.BOT;
        }
        String action = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
        return switch (action) {
            case "notify_author" -> AccessTokenKind.COMMUNITY;
            case "reply_comment" -> AccessTokenKind.USER;
            case "delete_comment", "restore_comment" -> AccessTokenKind.USER;
            case "publish_suggested", "delete_post", "ban_user", "unban_user" -> AccessTokenKind.USER;
            default -> AccessTokenKind.USER;
        };
    }

    private AccessRow load(UUID channelId) {
        return jdbcClient
                .sql(
                        """
                        SELECT c.thread_id,
                               c.external_id,
                               c.bot_token_enc,
                               sa.access_token_enc,
                               wdk.dek_enc
                        FROM channels c
                        LEFT JOIN social_accounts sa ON sa.id = c.social_account_id
                        LEFT JOIN workspace_data_keys wdk ON wdk.workspace_id = c.workspace_id
                        WHERE c.id = :channelId
                        """)
                .param("channelId", channelId)
                .query((rs, rowNum) -> new AccessRow(
                        rs.getString("thread_id"),
                        rs.getString("external_id"),
                        rs.getBytes("bot_token_enc"),
                        rs.getBytes("access_token_enc"),
                        rs.getBytes("dek_enc")))
                .optional()
                .orElse(null);
    }

    private String unsealPreferring(AccessRow row, AccessTokenKind kind) {
        if (row == null) {
            return fallbackToken;
        }
        if (kind == AccessTokenKind.USER) {
            String account = unseal(row.dekEnc(), row.accessTokenEnc());
            if (account != null && !account.isBlank()) {
                return account;
            }
            String bot = unseal(row.dekEnc(), row.botTokenEnc());
            if (bot != null && !bot.isBlank()) {
                return bot;
            }
            return fallbackToken;
        }
        // COMMUNITY / BOT — prefer channel bot token
        String custom = unseal(row.dekEnc(), row.botTokenEnc());
        if (custom != null && !custom.isBlank()) {
            return custom;
        }
        String account = unseal(row.dekEnc(), row.accessTokenEnc());
        if (account != null && !account.isBlank()) {
            return account;
        }
        return fallbackToken;
    }

    private String unseal(byte[] dekEnc, byte[] blob) {
        if (blob == null || blob.length == 0) {
            return null;
        }
        if (blob.length == 1 && blob[0] == 0) {
            return null;
        }
        if (dekEnc == null || dekEnc.length == 0) {
            return null;
        }
        try {
            byte[] dek = crypto.unwrapDek(dekEnc);
            return crypto.unseal(dek, blob);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    @SuppressWarnings("ArrayRecordComponent")
    private record AccessRow(
            String threadId, String externalId, byte[] botTokenEnc, byte[] accessTokenEnc, byte[] dekEnc) {}
}
