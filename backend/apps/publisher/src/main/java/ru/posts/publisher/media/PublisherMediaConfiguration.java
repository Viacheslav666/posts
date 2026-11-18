package ru.posts.publisher.media;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.posts.common.s3.S3ClientFactory;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties(PublisherS3Properties.class)
public class PublisherMediaConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "posts.s3", name = "enabled", havingValue = "true")
    S3Client publisherS3Client(PublisherS3Properties properties) {
        return S3ClientFactory.createClient(properties.toSettings());
    }
}
