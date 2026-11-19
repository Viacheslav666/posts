package ru.posts.social.inbound.subscription;

import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.TelegramBotEntity;

/** Resolves plaintext network tokens for subscription operations. */
public interface TokenResolver {
    String groupToken(ChannelEntity channel);

    String botToken(TelegramBotEntity bot);

    String channelBotToken(ChannelEntity channel);
}
