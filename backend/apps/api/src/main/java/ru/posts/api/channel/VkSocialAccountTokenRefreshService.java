package ru.posts.api.channel;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import ru.posts.persistence.entity.SocialAccountEntity;
import ru.posts.security.oauth.VkIdUserTokenRefresher;

@Service
@Profile("!openapi")
public class VkSocialAccountTokenRefreshService {

    private final VkIdUserTokenRefresher refresher;

    public VkSocialAccountTokenRefreshService(VkIdUserTokenRefresher refresher) {
        this.refresher = refresher;
    }

    public SocialAccountEntity ensureFresh(SocialAccountEntity account) {
        if (account == null || account.getId() == null || refresher == null) {
            return account;
        }
        SocialAccountEntity fresh = refresher.ensureFresh(account.getId());
        return fresh == null ? account : fresh;
    }

    public SocialAccountEntity ensureFresh(UUID accountId) {
        if (refresher == null) {
            return null;
        }
        return refresher.ensureFresh(accountId);
    }
}
