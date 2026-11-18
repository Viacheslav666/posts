package ru.posts.publisher.channel;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import ru.posts.security.crypto.TokenCryptoService;
import ru.posts.social.api.ChannelContext;
import ru.posts.social.api.Network;

/**
 * Resolves decrypted bot/access tokens and Telegram {@code thread_id} for publisher calls (ADR-0006).
 * Prefers {@code channels.bot_token_enc} (custom bot), then {@code social_accounts.access_token_enc},
 * then the configured fallback (emulator / platform default).
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

    public ChannelContext context(UUID workspaceId, UUID channelId, String externalId, Network network) {
        AccessRow row = load(channelId);
        String token = unsealPreferring(row);
        String threadId =
                row == null || row.threadId() == null || row.threadId().isBlank() ? null : row.threadId();
        return new ChannelContext(workspaceId, channelId, externalId, token, network, threadId);
    }

    private AccessRow load(UUID channelId) {
        return jdbcClient
                .sql(
                        """
                        SELECT c.thread_id,
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
                        rs.getBytes("bot_token_enc"),
                        rs.getBytes("access_token_enc"),
                        rs.getBytes("dek_enc")))
                .optional()
                .orElse(null);
    }

    private String unsealPreferring(AccessRow row) {
        if (row == null) {
            return fallbackToken;
        }
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
    private record AccessRow(String threadId, byte[] botTokenEnc, byte[] accessTokenEnc, byte[] dekEnc) {}
}
