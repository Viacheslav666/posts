package ru.posts.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI postsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Posts API")
                        .version("0.2.0")
                        .description(
                                """
                                Posts SMM platform public API (`/api/v1`).

                                BE-3 core contract: DTO + stub controllers (HTTP 501) except `GET /me` on the `local` profile.
                                Errors follow RFC 7807 ProblemDetail with a stable `code` property.
                                List endpoints use opaque cursor pagination (`cursor`, `limit`).
                                """)
                        .contact(new Contact().name("Posts").url("https://github.com/Viacheslav666/posts"))
                        .license(new License().name("Proprietary")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local API"),
                        new Server().url("https://api.posts.local").description("Staging placeholder")))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        "bearer-jwt",
                                        new SecurityScheme()
                                                .name("bearer-jwt")
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                                .description(
                                                        "Access token (ES256). Issued by Posts auth; JWKS at /.well-known/jwks.json.")));
    }
}
