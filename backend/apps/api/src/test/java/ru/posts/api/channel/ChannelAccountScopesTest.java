package ru.posts.api.channel;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import ru.posts.persistence.entity.SocialAccountEntity;

class ChannelAccountScopesTest {

    @Test
    void grantedScopesFromTokenResponseAreStored() {
        SocialAccountEntity account = new SocialAccountEntity();
        SocialConnectClient.OAuthToken token = new SocialConnectClient.OAuthToken(
                "vk2.a.token", "refresh", Instant.now(), "42", "Ivan", new String[] {"wall", "groups"});

        ChannelServiceImpl.applyGrantedScopes(account, token);

        assertThat(account.getScopes()).containsExactly("wall", "groups");
    }

    @Test
    void omittedScopeDoesNotClearStoredScopes() {
        SocialAccountEntity account = new SocialAccountEntity();
        account.setScopes(new String[] {"offline"});
        SocialConnectClient.OAuthToken token =
                new SocialConnectClient.OAuthToken("at", "rt", Instant.now(), "1", "Bot");

        ChannelServiceImpl.applyGrantedScopes(account, token);

        assertThat(account.getScopes()).containsExactly("offline");
    }
}
