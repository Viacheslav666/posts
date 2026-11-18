package ru.posts.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jwt.SignedJWT;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.posts.security.auth.SecurityAuthSupport;

class JwtServiceImpersonationTest {

    @Test
    void impersonationTokenCarriesImpAndActorClaims() throws Exception {
        JwtProperties properties = new JwtProperties();
        EcKeyProvider keys = new EcKeyProvider(properties);
        JwtService jwtService = new JwtService(keys, properties);

        UUID actor = UUID.fromString("019a0000-0002-7000-8000-0000000000aa");
        UUID target = UUID.fromString("019a0000-0002-7000-8000-0000000000bb");
        UUID session = UUID.fromString("019a0000-0002-7000-8000-0000000000cc");

        JwtService.IssuedAccessToken issued =
                jwtService.issueImpersonationAccessToken(actor, target, session, "support ticket #42");

        SignedJWT jwt = SignedJWT.parse(issued.token());
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(target.toString());
        assertThat(jwt.getJWTClaimsSet().getBooleanClaim(SecurityAuthSupport.CLAIM_IMPERSONATING))
                .isTrue();
        assertThat(jwt.getJWTClaimsSet().getStringClaim(SecurityAuthSupport.CLAIM_ACTOR))
                .isEqualTo(actor.toString());
        assertThat(jwt.getJWTClaimsSet().getStringClaim(SecurityAuthSupport.CLAIM_IMPERSONATION_REASON))
                .isEqualTo("support ticket #42");
        assertThat(jwt.getJWTClaimsSet().getStringClaim("sid")).isEqualTo(session.toString());
        assertThat(issued.expiresInSeconds())
                .isEqualTo(properties.getImpersonationTokenTtl().toSeconds());
    }
}
