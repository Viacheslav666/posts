package ru.posts.scheduler.inbound;

import java.util.UUID;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.TelegramBotEntity;
import ru.posts.persistence.entity.WorkspaceDataKeyEntity;
import ru.posts.persistence.repository.WorkspaceDataKeyRepository;
import ru.posts.security.crypto.TokenCryptoService;
import ru.posts.social.inbound.subscription.TokenResolver;

/**
 * Unseals stored channel and bot tokens. A missing or undecryptable secret stays null.
 * The configured platform bot token is used only for the explicit placeholder blob (a single 0 byte),
 * never as a stand-in for a real token that failed to unseal.
 */
public final class DecryptingTokenResolver implements TokenResolver {

    private final TokenCryptoService crypto;
    private final WorkspaceDataKeyRepository keys;
    private final String tgBotToken;
    private final String maxBotToken;

    public DecryptingTokenResolver(
            TokenCryptoService crypto, WorkspaceDataKeyRepository keys, String tgBotToken, String maxBotToken) {
        this.crypto = crypto;
        this.keys = keys;
        this.tgBotToken = tgBotToken;
        this.maxBotToken = maxBotToken;
    }

    static boolean placeholder(byte[] blob) {
        return blob == null || blob.length == 0 || (blob.length == 1 && blob[0] == 0);
    }

    @Override
    public String groupToken(ChannelEntity channel) {
        if (channel == null || placeholder(channel.getGroupTokenEnc())) {
            return null;
        }
        return unseal(channel.getWorkspaceId(), channel.getGroupTokenEnc());
    }

    @Override
    public String botToken(TelegramBotEntity bot) {
        if (bot == null) {
            return null;
        }
        if (placeholder(bot.getTokenEnc())) {
            return tgBotToken;
        }
        if (bot.getWorkspaceId() == null) {
            return null;
        }
        return unseal(bot.getWorkspaceId(), bot.getTokenEnc());
    }

    @Override
    public String channelBotToken(ChannelEntity channel) {
        if (channel == null) {
            return null;
        }
        if (!placeholder(channel.getBotTokenEnc())) {
            String unsealed = unseal(channel.getWorkspaceId(), channel.getBotTokenEnc());
            if (unsealed != null) {
                return unsealed;
            }
            return null;
        }
        return "max".equals(channel.getNetwork()) ? maxBotToken : tgBotToken;
    }

    private String unseal(UUID workspaceId, byte[] blob) {
        if (workspaceId == null || placeholder(blob)) {
            return null;
        }
        WorkspaceDataKeyEntity key = keys.findById(workspaceId).orElse(null);
        if (key == null || key.getDekEnc() == null) {
            return null;
        }
        try {
            return crypto.unseal(crypto.unwrapDek(key.getDekEnc()), blob);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
