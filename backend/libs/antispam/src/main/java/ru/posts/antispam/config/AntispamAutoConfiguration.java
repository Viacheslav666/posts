package ru.posts.antispam.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.posts.antispam.engine.CompiledRuleCache;
import ru.posts.antispam.handler.AntispamSocialEventHandler;
import ru.posts.antispam.metrics.AntispamMetrics;
import ru.posts.antispam.service.AdsContextResolver;
import ru.posts.antispam.service.AntispamActionApplier;
import ru.posts.antispam.service.AntispamEventProcessor;
import ru.posts.antispam.service.AntispamHistoryScanner;
import ru.posts.antispam.service.AntispamLogReconciler;
import ru.posts.antispam.service.MembershipCache;
import ru.posts.antispam.service.PinnedPostCache;
import ru.posts.antispam.service.RemoteRefFactory;
import ru.posts.antispam.service.RepeatDetector;
import ru.posts.antispam.service.SectionResolver;
import ru.posts.persistence.config.PersistenceAutoConfiguration;
import ru.posts.persistence.jdbc.AntispamLogJdbc;
import ru.posts.persistence.jdbc.OutboxJdbc;
import ru.posts.persistence.repository.AntispamRuleRepository;
import ru.posts.persistence.repository.AntispamWordListRepository;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.service.RemoteActionService;
import ru.posts.social.inbound.SocialEventHandler;

@AutoConfiguration(after = PersistenceAutoConfiguration.class)
@Profile("!openapi")
@ConditionalOnClass(SocialEventHandler.class)
public class AntispamAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AntispamMetrics antispamMetrics(ObjectProvider<MeterRegistry> registry) {
        return new AntispamMetrics(registry.getIfAvailable(SimpleMeterRegistry::new));
    }

    @Bean
    @ConditionalOnMissingBean
    CompiledRuleCache compiledRuleCache() {
        return new CompiledRuleCache();
    }

    @Bean
    @ConditionalOnMissingBean
    SectionResolver sectionResolver(ObjectProvider<ObjectMapper> objectMapper) {
        return new SectionResolver(objectMapper.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @ConditionalOnMissingBean
    RemoteRefFactory remoteRefFactory(ObjectProvider<ObjectMapper> objectMapper) {
        return new RemoteRefFactory(objectMapper.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @ConditionalOnMissingBean
    MembershipCache membershipCache(ObjectProvider<StringRedisTemplate> redis) {
        return new MembershipCache(redis);
    }

    @Bean
    @ConditionalOnMissingBean
    RepeatDetector repeatDetector(ObjectProvider<StringRedisTemplate> redis) {
        return new RepeatDetector(redis);
    }

    @Bean
    @ConditionalOnMissingBean
    PinnedPostCache pinnedPostCache() {
        return new PinnedPostCache();
    }

    @Bean
    @ConditionalOnMissingBean
    AdsContextResolver adsContextResolver(ObjectProvider<ObjectMapper> objectMapper) {
        return new AdsContextResolver(objectMapper.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({RemoteActionService.class, AntispamLogJdbc.class, OutboxJdbc.class})
    AntispamActionApplier antispamActionApplier(
            RemoteActionService remoteActionService,
            AntispamLogJdbc logJdbc,
            OutboxJdbc outboxJdbc,
            ObjectProvider<ObjectMapper> objectMapper,
            AntispamMetrics metrics,
            RemoteRefFactory remoteRefFactory) {
        return new AntispamActionApplier(
                remoteActionService,
                logJdbc,
                outboxJdbc,
                objectMapper.getIfAvailable(ObjectMapper::new),
                metrics,
                remoteRefFactory);
    }

    /**
     * Do not gate on JPA repository types via {@code @ConditionalOnBean}: repositories are
     * registered late and the condition often fails, leaving the processor absent at runtime.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(AntispamActionApplier.class)
    AntispamEventProcessor antispamEventProcessor(
            AntispamRuleRepository ruleRepository,
            AntispamWordListRepository wordListRepository,
            ChannelRepository channelRepository,
            CompiledRuleCache ruleCache,
            SectionResolver sectionResolver,
            MembershipCache membershipCache,
            RepeatDetector repeatDetector,
            AntispamActionApplier actionApplier,
            AntispamMetrics metrics,
            ObjectProvider<ObjectMapper> objectMapper,
            PinnedPostCache pinnedPostCache,
            AdsContextResolver adsContextResolver) {
        return new AntispamEventProcessor(
                ruleRepository,
                wordListRepository,
                channelRepository,
                ruleCache,
                sectionResolver,
                membershipCache,
                repeatDetector,
                actionApplier,
                metrics,
                objectMapper.getIfAvailable(ObjectMapper::new),
                pinnedPostCache,
                adsContextResolver);
    }

    @Bean
    @ConditionalOnMissingBean(AntispamSocialEventHandler.class)
    @ConditionalOnBean(AntispamEventProcessor.class)
    AntispamSocialEventHandler antispamSocialEventHandler(AntispamEventProcessor processor) {
        return new AntispamSocialEventHandler(processor);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({AntispamLogJdbc.class, JdbcTemplate.class})
    AntispamLogReconciler antispamLogReconciler(AntispamLogJdbc logJdbc, JdbcTemplate jdbc) {
        return new AntispamLogReconciler(logJdbc, jdbc);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(AntispamEventProcessor.class)
    AntispamHistoryScanner antispamHistoryScanner(
            AntispamEventProcessor processor,
            @Value("${posts.social.vk.base-url:${posts.social-inbound.social-base-url:http://127.0.0.1:8090}}")
                    String baseUrl,
            @Value("${posts.social.vk.api-version:5.199}") String apiVersion) {
        return new AntispamHistoryScanner(processor, baseUrl, apiVersion);
    }
}
