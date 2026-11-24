package ru.posts.api.channel;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.persistence.entity.SocialAccountEntity;
import ru.posts.persistence.repository.SocialAccountRepository;

@Service
@Profile("!openapi")
public class VkSocialAccountTokenRefreshService {

    private final ChannelProperties properties;
    private final SocialConnectClient connectClient;
    private final WorkspaceTokenVault tokenVault;
    private final SocialAccountRepository socialAccountRepository;

    public VkSocialAccountTokenRefreshService(
            ChannelProperties properties,
            SocialConnectClient connectClient,
            WorkspaceTokenVault tokenVault,
            SocialAccountRepository socialAccountRepository) {
        this.properties = properties;
        this.connectClient = connectClient;
        this.tokenVault = tokenVault;
        this.socialAccountRepository = socialAccountRepository;
    }

    @Transactional
    public SocialAccountEntity ensureFresh(SocialAccountEntity account) {
        if (account == null || !"vk".equals(account.getNetwork()) || !properties.vkIdMode()) {
            return account;
        }
        Instant expiresAt = account.getTokenExpiresAt();
        if (expiresAt != null && expiresAt.isAfter(Instant.now().plus(5, ChronoUnit.MINUTES))) {
            return account;
        }
        if (account.getRefreshTokenEnc() == null || account.getRefreshTokenEnc().length == 0) {
            return account;
        }
        String refresh = tokenVault.unseal(account.getWorkspaceId(), account.getRefreshTokenEnc());
        if (refresh == null || refresh.isBlank()) {
            return account;
        }
        SocialConnectClient.OAuthToken refreshed = connectClient.refreshVk(refresh, account.getDeviceId(), null);
        account.setAccessTokenEnc(tokenVault.seal(account.getWorkspaceId(), refreshed.accessToken()));
        if (refreshed.refreshToken() != null && !refreshed.refreshToken().isBlank()) {
            account.setRefreshTokenEnc(tokenVault.seal(account.getWorkspaceId(), refreshed.refreshToken()));
        }
        account.setTokenExpiresAt(refreshed.expiresAt());
        account.setUpdatedAt(Instant.now());
        return socialAccountRepository.save(account);
    }
}
