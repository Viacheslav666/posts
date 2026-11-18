package ru.posts.security.jwt;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "posts.auth.jwt")
public class JwtProperties {

    /** Active signing key id published in JWKS. */
    private String keyId = "posts-es256-1";

    /** PKCS8 EC private key PEM (ES256). Empty → ephemeral key for local/test. */
    private String privateKeyPem = "";

    /** X.509 EC public key PEM. Optional when private key is set (derived). */
    private String publicKeyPem = "";

    private Duration accessTokenTtl = Duration.ofMinutes(15);

    /** Short-lived access token TTL for platform-admin impersonation ("login as"). */
    private Duration impersonationTokenTtl = Duration.ofMinutes(30);

    private Duration refreshTokenTtl = Duration.ofDays(30);

    private String issuer = "https://posts.ru";

    private String audience = "posts-api";

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getPrivateKeyPem() {
        return privateKeyPem;
    }

    public void setPrivateKeyPem(String privateKeyPem) {
        this.privateKeyPem = privateKeyPem;
    }

    public String getPublicKeyPem() {
        return publicKeyPem;
    }

    public void setPublicKeyPem(String publicKeyPem) {
        this.publicKeyPem = publicKeyPem;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public Duration getImpersonationTokenTtl() {
        return impersonationTokenTtl;
    }

    public void setImpersonationTokenTtl(Duration impersonationTokenTtl) {
        this.impersonationTokenTtl = impersonationTokenTtl;
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }
}
