package ru.posts.api.automation.suggest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import ru.posts.api.channel.VkSocialAccountTokenRefreshService;
import ru.posts.api.channel.WorkspaceTokenVault;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.SocialAccountEntity;
import ru.posts.persistence.repository.SocialAccountRepository;
import ru.posts.suggest.sync.SuggestSyncService.SuggestTokenResolver;

@Configuration
@Profile("!openapi")
public class ApiSuggestTokenResolverConfig {

    @Bean
    @Primary
    SuggestTokenResolver apiSuggestTokenResolver(
            SocialAccountRepository socialAccountRepository,
            WorkspaceTokenVault tokenVault,
            VkSocialAccountTokenRefreshService vkTokenRefresh) {
        return (ChannelEntity channel) -> {
            if (channel == null || channel.getSocialAccountId() == null) {
                return null;
            }
            SocialAccountEntity account = socialAccountRepository
                    .findById(channel.getSocialAccountId())
                    .orElse(null);
            if (account == null || account.getAccessTokenEnc() == null) {
                return null;
            }
            SocialAccountEntity fresh = vkTokenRefresh.ensureFresh(account);
            return tokenVault.unseal(channel.getWorkspaceId(), fresh.getAccessTokenEnc());
        };
    }
}
