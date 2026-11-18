package ru.posts.security.jwt;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import ru.posts.security.auth.SecurityAuthSupport;

public final class JwtService {

    private final EcKeyProvider keyProvider;
    private final JwtProperties properties;

    public JwtService(EcKeyProvider keyProvider, JwtProperties properties) {
        this.keyProvider = keyProvider;
        this.properties = properties;
    }

    public IssuedAccessToken issueAccessToken(UUID userId, UUID sessionId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getAccessTokenTtl());
        JWTClaimsSet.Builder builder = baseClaims(userId, sessionId, now, expiresAt);
        return sign(builder.build(), properties.getAccessTokenTtl().toSeconds(), expiresAt);
    }

    /**
     * Issues a short-lived access token where {@code sub} is the target user and {@code act} is the
     * platform admin. Claim {@code imp=true} drives the FE impersonation banner.
     */
    public IssuedAccessToken issueImpersonationAccessToken(
            UUID actorUserId, UUID targetUserId, UUID sessionId, String reason) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getImpersonationTokenTtl());
        JWTClaimsSet.Builder builder = baseClaims(targetUserId, sessionId, now, expiresAt)
                .claim(SecurityAuthSupport.CLAIM_IMPERSONATING, true)
                .claim(SecurityAuthSupport.CLAIM_ACTOR, actorUserId.toString())
                .claim(SecurityAuthSupport.CLAIM_IMPERSONATION_REASON, reason == null ? "" : reason);
        return sign(builder.build(), properties.getImpersonationTokenTtl().toSeconds(), expiresAt);
    }

    private JWTClaimsSet.Builder baseClaims(UUID subject, UUID sessionId, Instant now, Instant expiresAt) {
        return new JWTClaimsSet.Builder()
                .issuer(properties.getIssuer())
                .audience(properties.getAudience())
                .subject(subject.toString())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .claim("sid", sessionId.toString());
    }

    private IssuedAccessToken sign(JWTClaimsSet claims, long expiresInSeconds, Instant expiresAt) {
        try {
            ECKey key = keyProvider.ecKey();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.ES256)
                            .keyID(key.getKeyID())
                            .type(JOSEObjectType.JWT)
                            .build(),
                    claims);
            jwt.sign(new ECDSASigner(key.toECPrivateKey()));
            return new IssuedAccessToken(jwt.serialize(), expiresInSeconds, expiresAt);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to issue access token", ex);
        }
    }

    public record IssuedAccessToken(String token, long expiresInSeconds, Instant expiresAt) {}
}
