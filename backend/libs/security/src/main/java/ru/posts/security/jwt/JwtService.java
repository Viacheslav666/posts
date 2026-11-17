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
        try {
            ECKey key = keyProvider.ecKey();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(properties.getIssuer())
                    .audience(properties.getAudience())
                    .subject(userId.toString())
                    .jwtID(UUID.randomUUID().toString())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(expiresAt))
                    .claim("sid", sessionId.toString())
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.ES256)
                            .keyID(key.getKeyID())
                            .type(JOSEObjectType.JWT)
                            .build(),
                    claims);
            jwt.sign(new ECDSASigner(key.toECPrivateKey()));
            return new IssuedAccessToken(
                    jwt.serialize(), properties.getAccessTokenTtl().toSeconds(), expiresAt);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to issue access token", ex);
        }
    }

    public record IssuedAccessToken(String token, long expiresInSeconds, Instant expiresAt) {}
}
