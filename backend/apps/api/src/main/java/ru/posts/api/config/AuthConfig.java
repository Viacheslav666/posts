package ru.posts.api.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestClient;
import ru.posts.api.auth.AuthProperties;
import ru.posts.api.auth.crypto.SecretBox;
import ru.posts.api.auth.mail.AuthMailSender;
import ru.posts.api.auth.mail.LoggingAuthMailSender;
import ru.posts.api.auth.mail.SmtpAuthMailSender;
import ru.posts.api.auth.oauth.OAuthProviderClient;
import ru.posts.api.auth.oauth.RestOAuthProviderClient;
import ru.posts.api.auth.oauth.StubOAuthProviderClient;
import ru.posts.ratelimit.login.InMemoryLoginRateLimiter;
import ru.posts.ratelimit.login.LoginRateLimiter;
import ru.posts.security.jwt.EcKeyProvider;
import ru.posts.security.jwt.JwksService;
import ru.posts.security.jwt.JwtProperties;
import ru.posts.security.jwt.JwtService;
import ru.posts.security.password.HibpPasswordChecker;
import ru.posts.security.password.PasswordService;
import ru.posts.security.totp.TotpService;

@Configuration
@EnableConfigurationProperties({AuthProperties.class, JwtProperties.class})
public class AuthConfig {

    @Bean
    EcKeyProvider ecKeyProvider(JwtProperties jwtProperties) {
        return new EcKeyProvider(jwtProperties);
    }

    @Bean
    JwtService jwtService(EcKeyProvider ecKeyProvider, JwtProperties jwtProperties) {
        return new JwtService(ecKeyProvider, jwtProperties);
    }

    @Bean
    JwksService jwksService(EcKeyProvider ecKeyProvider) {
        return new JwksService(ecKeyProvider);
    }

    @Bean
    JwtDecoder jwtDecoder(EcKeyProvider ecKeyProvider, JwtProperties jwtProperties) {
        JWKSet jwkSet = new JWKSet(ecKeyProvider.publicJwk());
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(
                new JWSVerificationKeySelector<>(JWSAlgorithm.ES256, new ImmutableJWKSet<>(jwkSet)));
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
        OAuth2TokenValidator<Jwt> validator =
                new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(jwtProperties.getIssuer()));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    @Bean
    PasswordService passwordService(AuthProperties authProperties) {
        return new PasswordService(new HibpPasswordChecker.NoOp(), authProperties.isHibpEnabled());
    }

    @Bean
    TotpService totpService() {
        return new TotpService();
    }

    @Bean
    SecretBox secretBox() {
        String key = System.getenv().getOrDefault("POSTS_AUTH_TOTP_KEY", "local-dev-totp-key");
        return new SecretBox(key);
    }

    @Bean
    LoginRateLimiter loginRateLimiter() {
        return new InMemoryLoginRateLimiter();
    }

    @Bean
    @Profile("!test & !openapi")
    @ConditionalOnProperty(prefix = "spring.mail", name = "host")
    AuthMailSender smtpAuthMailSender(JavaMailSender mailSender, AuthProperties properties) {
        return new SmtpAuthMailSender(mailSender, properties);
    }

    @Bean
    @ConditionalOnMissingBean(AuthMailSender.class)
    LoggingAuthMailSender loggingAuthMailSender() {
        return new LoggingAuthMailSender();
    }

    @Bean
    @Profile({"test", "local", "openapi"})
    StubOAuthProviderClient stubOAuthProviderClient() {
        return new StubOAuthProviderClient();
    }

    @Bean
    @Profile("!test & !local & !openapi")
    OAuthProviderClient restOAuthProviderClient(AuthProperties properties, RestClient.Builder builder) {
        return new RestOAuthProviderClient(properties, builder);
    }

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
