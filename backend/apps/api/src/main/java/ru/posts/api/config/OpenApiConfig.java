package ru.posts.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.posts.api.error.ProblemCodes;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI postsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Posts API")
                        .version("0.3.0")
                        .description(
                                """
                                Posts SMM platform public API (`/api/v1`).

                                BE-5 auth is implemented (register/verify/login/refresh/logout, JWT ES256+JWKS, 2FA, OAuth VK/Yandex, sessions).
                                Other domain controllers may still return 501 until later BE tasks.
                                Errors follow RFC 7807 ProblemDetail with a stable `code` property.
                                List endpoints use opaque cursor pagination (`cursor`, `limit`) with typed `*Page` schemas.

                                """
                                        + ProblemCodes.OPENAPI_CATALOG
                                        + """

                                Auth: most operations require `Authorization: Bearer <access_jwt>` (scheme `bearer-jwt`).
                                Public: `/api/v1/ping`, `/api/v1/auth/*` (except 2FA setup/confirm/disable, oauth link), invitation preview.
                                JWKS: `GET /.well-known/jwks.json`.
                                IDOR: foreign workspace/resource → 404 `not_found` (not 403).
                                """)
                        .contact(new Contact().name("Posts").url("https://github.com/Viacheslav666/posts"))
                        .license(new License().name("Proprietary")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local API"),
                        new Server().url("https://api.posts.local").description("Staging placeholder")))
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"))
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

    /**
     * {@code {mediaId:uuid-regex}} keeps literal routes like {@code /folders} from binding as an id.
     * Publish the path as {@code {mediaId}} so the generated client stays stable.
     */
    @Bean
    OpenApiCustomizer normalizeMediaIdPaths() {
        return OpenApiConfig::rewriteMediaIdPaths;
    }

    static void rewriteMediaIdPaths(OpenAPI openApi) {
        if (openApi.getPaths() == null) {
            return;
        }
        Paths next = new Paths();
        openApi.getPaths().forEach((path, item) -> {
            String normalized = path.replaceAll("\\{mediaId:[^}]*}", "{mediaId}");
            next.addPathItem(normalized, item);
        });
        openApi.setPaths(next);
    }
}
