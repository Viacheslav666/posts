package ru.posts.social.inbound.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import ru.posts.persistence.repository.ChannelEventSubscriptionRepository;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.TelegramBotRepository;
import ru.posts.social.inbound.RecordingSocialEventHandler;
import ru.posts.social.inbound.SocialEventDispatcher;
import ru.posts.social.inbound.SocialEventHandler;
import ru.posts.social.inbound.SocialInboundProcessor;
import ru.posts.social.inbound.album.AlbumFragmentStore;
import ru.posts.social.inbound.album.InMemoryAlbumFragmentStore;
import ru.posts.social.inbound.album.JdbcAlbumFragmentStore;
import ru.posts.social.inbound.album.TelegramAlbumAggregator;
import ru.posts.social.inbound.client.MaxSubscriptionClient;
import ru.posts.social.inbound.client.TelegramWebhookClient;
import ru.posts.social.inbound.client.VkCallbackClient;
import ru.posts.social.inbound.dedup.InboundEventStore;
import ru.posts.social.inbound.metrics.SocialInboundMetrics;
import ru.posts.social.inbound.subscription.EventSubscriptionService;
import ru.posts.social.inbound.subscription.SecretCrypto;
import ru.posts.social.inbound.subscription.TokenResolver;

@AutoConfiguration
@EnableConfigurationProperties(SocialInboundProperties.class)
public class SocialInboundAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    SocialInboundMetrics socialInboundMetrics(ObjectProvider<MeterRegistry> registry) {
        return new SocialInboundMetrics(registry.getIfAvailable(SimpleMeterRegistry::new));
    }

    @Bean
    @ConditionalOnMissingBean
    SocialEventDispatcher socialEventDispatcher(List<SocialEventHandler> handlers, SocialInboundMetrics metrics) {
        return new SocialEventDispatcher(handlers == null ? List.of() : handlers, metrics);
    }

    @Bean
    @ConditionalOnMissingBean
    VkCallbackClient vkCallbackClient(SocialInboundProperties properties) {
        return new VkCallbackClient(properties.getSocialBaseUrl(), properties.getVkApiVersion());
    }

    @Bean
    @ConditionalOnMissingBean
    TelegramWebhookClient telegramWebhookClient(SocialInboundProperties properties) {
        return new TelegramWebhookClient(properties.getSocialBaseUrl());
    }

    @Bean
    @ConditionalOnMissingBean
    MaxSubscriptionClient maxSubscriptionClient(SocialInboundProperties properties) {
        return new MaxSubscriptionClient(properties.getSocialBaseUrl());
    }

    @Bean
    @ConditionalOnBean(JdbcClient.class)
    @ConditionalOnMissingBean(AlbumFragmentStore.class)
    AlbumFragmentStore jdbcAlbumFragmentStore(JdbcClient jdbc, ObjectMapper objectMapper) {
        return new JdbcAlbumFragmentStore(jdbc, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(AlbumFragmentStore.class)
    AlbumFragmentStore memoryAlbumFragmentStore(ObjectMapper objectMapper) {
        return new InMemoryAlbumFragmentStore(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    TelegramAlbumAggregator telegramAlbumAggregator(AlbumFragmentStore albumFragmentStore) {
        return new TelegramAlbumAggregator(albumFragmentStore);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean
    InboundEventStore inboundEventStore(JdbcClient jdbc, ObjectMapper objectMapper) {
        return new InboundEventStore(jdbc, objectMapper);
    }

    @Bean
    @ConditionalOnBean({
        DataSource.class,
        InboundEventStore.class,
        ChannelRepository.class,
        ChannelEventSubscriptionRepository.class,
        TelegramBotRepository.class
    })
    @ConditionalOnMissingBean
    SocialInboundProcessor socialInboundProcessor(
            ObjectMapper objectMapper,
            ChannelRepository channelRepository,
            ChannelEventSubscriptionRepository subscriptionRepository,
            TelegramBotRepository telegramBotRepository,
            InboundEventStore eventStore,
            SocialEventDispatcher dispatcher,
            SocialInboundMetrics metrics,
            TelegramAlbumAggregator albumAggregator) {
        return new SocialInboundProcessor(
                objectMapper,
                channelRepository,
                subscriptionRepository,
                telegramBotRepository,
                eventStore,
                dispatcher,
                metrics,
                albumAggregator);
    }

    @Bean
    @ConditionalOnBean({SecretCrypto.class, TokenResolver.class, DataSource.class})
    @ConditionalOnMissingBean
    EventSubscriptionService eventSubscriptionService(
            ChannelRepository channelRepository,
            ChannelEventSubscriptionRepository subscriptionRepository,
            TelegramBotRepository telegramBotRepository,
            SecretCrypto secretCrypto,
            TokenResolver tokenResolver,
            SocialInboundProperties properties,
            VkCallbackClient vkCallbackClient,
            TelegramWebhookClient telegramWebhookClient,
            MaxSubscriptionClient maxSubscriptionClient) {
        return new EventSubscriptionService(
                channelRepository,
                subscriptionRepository,
                telegramBotRepository,
                secretCrypto,
                tokenResolver,
                properties,
                vkCallbackClient,
                telegramWebhookClient,
                maxSubscriptionClient);
    }

    @Bean
    @ConditionalOnProperty(name = "posts.social-inbound.recording-handler", havingValue = "true")
    RecordingSocialEventHandler recordingSocialEventHandler() {
        return new RecordingSocialEventHandler();
    }
}
