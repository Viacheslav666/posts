package ru.posts.antispam.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import ru.posts.antispam.engine.CompiledRuleCache;
import ru.posts.antispam.handler.AntispamSocialEventHandler;
import ru.posts.antispam.metrics.AntispamMetrics;
import ru.posts.antispam.service.AdsContextResolver;
import ru.posts.antispam.service.AntispamActionApplier;
import ru.posts.antispam.service.AntispamEventProcessor;
import ru.posts.antispam.service.MembershipCache;
import ru.posts.antispam.service.PinnedPostCache;
import ru.posts.antispam.service.RemoteRefFactory;
import ru.posts.antispam.service.RepeatDetector;
import ru.posts.antispam.service.SectionResolver;
import ru.posts.persistence.jdbc.AntispamLogJdbc;
import ru.posts.persistence.jdbc.OutboxJdbc;
import ru.posts.persistence.repository.AntispamRuleRepository;
import ru.posts.persistence.repository.AntispamWordListRepository;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.service.RemoteActionService;
import ru.posts.social.inbound.SocialEventHandler;

@AutoConfiguration
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

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({AntispamRuleRepository.class, AntispamActionApplier.class})
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
}
