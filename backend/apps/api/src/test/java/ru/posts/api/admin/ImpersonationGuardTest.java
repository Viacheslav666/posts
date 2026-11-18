package ru.posts.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.security.auth.PostsPrincipal;
import ru.posts.security.auth.SecurityAuthSupport;

class ImpersonationGuardTest {

    private final ImpersonationGuard guard = new ImpersonationGuard();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowsWhenNotImpersonating() {
        assertThatCode(() -> guard.forbidIfImpersonating(new PostsPrincipal(UUID.randomUUID(), null), "x"))
                .doesNotThrowAnyException();
    }

    @Test
    void blocksWhenImpersonating() {
        UUID target = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject(target.toString())
                .issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusSeconds(600))
                .claim("sid", UUID.randomUUID().toString())
                .claim(SecurityAuthSupport.CLAIM_IMPERSONATING, true)
                .claim(SecurityAuthSupport.CLAIM_ACTOR, actor.toString())
                .claim(SecurityAuthSupport.CLAIM_IMPERSONATION_REASON, "help")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertThatThrownBy(() -> guard.forbidIfImpersonating("change_password"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ProblemCodes.IMPERSONATION_FORBIDDEN);
    }

    @Test
    void principalExposesEffectiveActor() {
        UUID target = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        PostsPrincipal principal = new PostsPrincipal(target, UUID.randomUUID(), true, actor, "help", null);
        assertThat(principal.effectiveActorId()).isEqualTo(actor);
        assertThat(new PostsPrincipal(target, null).effectiveActorId()).isEqualTo(target);
    }
}
