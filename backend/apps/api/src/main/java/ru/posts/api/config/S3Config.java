package ru.posts.api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import ru.posts.common.s3.S3ClientFactory;
import ru.posts.common.s3.S3Settings;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@Profile("!openapi")
@EnableConfigurationProperties(S3Properties.class)
public class S3Config {

    @Bean(destroyMethod = "close")
    S3Client s3Client(S3Properties properties) {
        S3Settings settings = properties.toSettings();
        return S3ClientFactory.createClient(settings);
    }

    @Bean(destroyMethod = "close")
    S3Presigner s3Presigner(S3Properties properties) {
        return S3ClientFactory.createPresigner(properties.toSettings());
    }
}
