package ru.posts.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
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
                        .version("0.1.0")
                        .description("Posts SMM platform API (DEV-1 skeleton)"))
                .servers(List.of(new Server().url("http://localhost:8080").description("Local API")));
    }
}
