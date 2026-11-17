package ru.posts.api.auth;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "posts.auth")
public class AuthProperties {

    private boolean hibpEnabled = false;
    private String appBaseUrl = "http://localhost:3000";
    private String mailFrom = "Posts <noreply@posts.local>";
    private String totpIssuer = "Posts";
    private Duration emailVerifyTtl = Duration.ofHours(24);
    private Duration passwordResetTtl = Duration.ofHours(1);
    private Duration oauthStateTtl = Duration.ofMinutes(10);
    private Duration oauthLinkTtl = Duration.ofMinutes(30);
    private String refreshCookieName = "posts_refresh";
    private boolean refreshCookieSecure = false;
    private String refreshCookieSameSite = "Lax";
    private Map<String, OAuthProviderProperties> oauth = new LinkedHashMap<>();

    public boolean isHibpEnabled() {
        return hibpEnabled;
    }

    public void setHibpEnabled(boolean hibpEnabled) {
        this.hibpEnabled = hibpEnabled;
    }

    public String getAppBaseUrl() {
        return appBaseUrl;
    }

    public void setAppBaseUrl(String appBaseUrl) {
        this.appBaseUrl = appBaseUrl;
    }

    public String getMailFrom() {
        return mailFrom;
    }

    public void setMailFrom(String mailFrom) {
        this.mailFrom = mailFrom;
    }

    public String getTotpIssuer() {
        return totpIssuer;
    }

    public void setTotpIssuer(String totpIssuer) {
        this.totpIssuer = totpIssuer;
    }

    public Duration getEmailVerifyTtl() {
        return emailVerifyTtl;
    }

    public void setEmailVerifyTtl(Duration emailVerifyTtl) {
        this.emailVerifyTtl = emailVerifyTtl;
    }

    public Duration getPasswordResetTtl() {
        return passwordResetTtl;
    }

    public void setPasswordResetTtl(Duration passwordResetTtl) {
        this.passwordResetTtl = passwordResetTtl;
    }

    public Duration getOauthStateTtl() {
        return oauthStateTtl;
    }

    public void setOauthStateTtl(Duration oauthStateTtl) {
        this.oauthStateTtl = oauthStateTtl;
    }

    public Duration getOauthLinkTtl() {
        return oauthLinkTtl;
    }

    public void setOauthLinkTtl(Duration oauthLinkTtl) {
        this.oauthLinkTtl = oauthLinkTtl;
    }

    public String getRefreshCookieName() {
        return refreshCookieName;
    }

    public void setRefreshCookieName(String refreshCookieName) {
        this.refreshCookieName = refreshCookieName;
    }

    public boolean isRefreshCookieSecure() {
        return refreshCookieSecure;
    }

    public void setRefreshCookieSecure(boolean refreshCookieSecure) {
        this.refreshCookieSecure = refreshCookieSecure;
    }

    public String getRefreshCookieSameSite() {
        return refreshCookieSameSite;
    }

    public void setRefreshCookieSameSite(String refreshCookieSameSite) {
        this.refreshCookieSameSite = refreshCookieSameSite;
    }

    public Map<String, OAuthProviderProperties> getOauth() {
        return oauth;
    }

    public void setOauth(Map<String, OAuthProviderProperties> oauth) {
        this.oauth = oauth;
    }

    public static class OAuthProviderProperties {
        private String clientId = "local-client";
        private String clientSecret = "local-secret";
        private String authorizationUri = "https://example.invalid/authorize";
        private String tokenUri = "https://example.invalid/token";
        private String userInfoUri = "https://example.invalid/userinfo";
        private String redirectUri = "http://localhost:3000/auth/callback";
        private String scopes = "openid email profile";

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getAuthorizationUri() {
            return authorizationUri;
        }

        public void setAuthorizationUri(String authorizationUri) {
            this.authorizationUri = authorizationUri;
        }

        public String getTokenUri() {
            return tokenUri;
        }

        public void setTokenUri(String tokenUri) {
            this.tokenUri = tokenUri;
        }

        public String getUserInfoUri() {
            return userInfoUri;
        }

        public void setUserInfoUri(String userInfoUri) {
            this.userInfoUri = userInfoUri;
        }

        public String getRedirectUri() {
            return redirectUri;
        }

        public void setRedirectUri(String redirectUri) {
            this.redirectUri = redirectUri;
        }

        public String getScopes() {
            return scopes;
        }

        public void setScopes(String scopes) {
            this.scopes = scopes;
        }
    }
}
