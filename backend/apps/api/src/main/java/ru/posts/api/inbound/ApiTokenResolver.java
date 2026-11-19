package ru.posts.api.inbound;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import ru.posts.api.channel.ChannelProperties;
import ru.posts.api.channel.WorkspaceTokenVault;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.TelegramBotEntity;
import ru.posts.social.inbound.subscription.TokenResolver;

@Component
@Profile("!openapi")
public class ApiTokenResolver implements TokenResolver {

    private final WorkspaceTokenVault vault;
    private final ChannelProperties channelProperties;

    public ApiTokenResolver(WorkspaceTokenVault vault, ChannelProperties channelProperties) {
        this.vault = vault;
        this.channelProperties = channelProperties;
    }

    @Override
    public String groupToken(ChannelEntity channel) {
        String unsealed = vault.unseal(channel.getWorkspaceId(), channel.getGroupTokenEnc());
        if (unsealed != null && !unsealed.isBlank()) {
            return unsealed;
        }
        // Emulator / local: allow placeholder group tokens stored as plaintext seed
        if (channel.getGroupTokenEnc() != null && channel.getGroupTokenEnc().length > 1) {
            return new String(channel.getGroupTokenEnc(), java.nio.charset.StandardCharsets.UTF_8);
        }
        return "emulator-group-token";
    }

    @Override
    public String botToken(TelegramBotEntity bot) {
        if (bot.getTokenEnc() != null && bot.getTokenEnc().length == 1 && bot.getTokenEnc()[0] == 0) {
            return channelProperties.tgBotToken();
        }
        if (bot.getWorkspaceId() != null) {
            String unsealed = vault.unseal(bot.getWorkspaceId(), bot.getTokenEnc());
            if (unsealed != null) {
                return unsealed;
            }
        }
        return channelProperties.tgBotToken();
    }

    @Override
    public String channelBotToken(ChannelEntity channel) {
        String unsealed = vault.unseal(channel.getWorkspaceId(), channel.getBotTokenEnc());
        if (unsealed != null && !unsealed.isBlank()) {
            return unsealed;
        }
        if ("max".equals(channel.getNetwork())) {
            return channelProperties.maxBotToken();
        }
        return channelProperties.tgBotToken();
    }
}
