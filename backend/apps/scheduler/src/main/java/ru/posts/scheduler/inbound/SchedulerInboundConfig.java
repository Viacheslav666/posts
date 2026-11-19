package ru.posts.scheduler.inbound;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.posts.persistence.entity.OutboxEntity;
import ru.posts.persistence.repository.OutboxRepository;
import ru.posts.persistence.repository.WorkspaceDataKeyRepository;
import ru.posts.security.crypto.TokenCryptoService;
import ru.posts.social.inbound.subscription.SecretCrypto;
import ru.posts.social.inbound.subscription.SubscriptionNotifier;
import ru.posts.social.inbound.subscription.TokenResolver;

@Configuration
public class SchedulerInboundConfig {

    @Bean
    @ConditionalOnMissingBean
    TokenCryptoService schedulerTokenCryptoService(@Value("${posts.crypto.kek:local-dev-posts-kek}") String kek) {
        return TokenCryptoService.fromKeyMaterial(kek);
    }

    @Bean
    @ConditionalOnMissingBean(SecretCrypto.class)
    SecretCrypto schedulerSecretCrypto(TokenCryptoService crypto, WorkspaceDataKeyRepository keys) {
        return new SecretCrypto() {
            @Override
            public byte[] seal(UUID workspaceId, String plaintext) {
                byte[] dek = keys.findById(workspaceId)
                        .map(row -> crypto.unwrapDek(row.getDekEnc()))
                        .orElseThrow(() -> new IllegalStateException("Missing workspace DEK"));
                return crypto.seal(dek, plaintext);
            }

            @Override
            public String unseal(UUID workspaceId, byte[] blob) {
                if (blob == null || blob.length == 0 || (blob.length == 1 && blob[0] == 0)) {
                    return null;
                }
                byte[] dek = keys.findById(workspaceId)
                        .map(row -> crypto.unwrapDek(row.getDekEnc()))
                        .orElseThrow(() -> new IllegalStateException("Missing workspace DEK"));
                return crypto.unseal(dek, blob);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(TokenResolver.class)
    TokenResolver schedulerTokenResolver(
            TokenCryptoService crypto,
            WorkspaceDataKeyRepository keys,
            @Value("${posts.channels.tg-bot-token:emulator-token}") String tgBotToken,
            @Value("${posts.channels.max-bot-token:emulator-max-token}") String maxBotToken) {
        return new DecryptingTokenResolver(crypto, keys, tgBotToken, maxBotToken);
    }

    @Bean
    @ConditionalOnMissingBean(SubscriptionNotifier.class)
    SubscriptionNotifier schedulerSubscriptionNotifier(OutboxRepository outboxRepository) {
        return (workspaceId, channelId, network, message, details) -> {
            OutboxEntity event = new OutboxEntity();
            event.setAggregate("channel");
            event.setAggregateId(channelId);
            event.setEventType("channel.event_subscription_degraded");
            Map<String, Object> payload = new HashMap<>();
            payload.put("workspaceId", workspaceId.toString());
            payload.put("channelId", channelId.toString());
            payload.put("network", network);
            payload.put("message", message);
            if (details != null) {
                payload.putAll(details);
            }
            event.setPayload(payload);
            outboxRepository.save(event);
        };
    }
}
