package ru.posts.media.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.posts.common.s3.S3ClientFactory;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties({S3Properties.class, MediaProperties.class})
public class S3Config {

    @Bean(destroyMethod = "close")
    S3Client s3Client(S3Properties properties) {
        return S3ClientFactory.createClient(properties.toSettings());
    }
}
