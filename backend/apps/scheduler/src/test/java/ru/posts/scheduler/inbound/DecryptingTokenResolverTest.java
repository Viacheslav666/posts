package ru.posts.scheduler.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.TelegramBotEntity;
import ru.posts.persistence.entity.WorkspaceDataKeyEntity;
import ru.posts.persistence.repository.WorkspaceDataKeyRepository;
import ru.posts.security.crypto.TokenCryptoService;

@ExtendWith(MockitoExtension.class)
class DecryptingTokenResolverTest {

    @Mock
    WorkspaceDataKeyRepository keys;

    @Test
    void realTokenUnsealsAndAFailureIsNotTheEmulatorToken() {
        TokenCryptoService crypto = TokenCryptoService.fromKeyMaterial("scheduler-kek");
        byte[] dek = crypto.generateDek();
        UUID workspaceId = UUID.randomUUID();
        WorkspaceDataKeyEntity key = new WorkspaceDataKeyEntity();
        key.setWorkspaceId(workspaceId);
        key.setDekEnc(crypto.wrapDek(dek));
        when(keys.findById(workspaceId)).thenReturn(Optional.of(key));

        DecryptingTokenResolver resolver = new DecryptingTokenResolver(crypto, keys, "platform-tg", "platform-max");
        ChannelEntity channel = new ChannelEntity();
        channel.setWorkspaceId(workspaceId);
        channel.setNetwork("vk");
        channel.setGroupTokenEnc(crypto.seal(dek, "real-group-token"));
        assertThat(resolver.groupToken(channel)).isEqualTo("real-group-token");

        channel.setGroupTokenEnc(crypto.seal(crypto.generateDek(), "other"));
        assertThat(resolver.groupToken(channel)).isNull();

        channel.setGroupTokenEnc(new byte[] {0});
        assertThat(resolver.groupToken(channel)).isNull();

        TelegramBotEntity bot = new TelegramBotEntity();
        bot.setWorkspaceId(workspaceId);
        bot.setTokenEnc(new byte[] {0});
        assertThat(resolver.botToken(bot)).isEqualTo("platform-tg");

        bot.setTokenEnc(crypto.seal(crypto.generateDek(), "broken-bot"));
        assertThat(resolver.botToken(bot)).isNull();
    }
}
