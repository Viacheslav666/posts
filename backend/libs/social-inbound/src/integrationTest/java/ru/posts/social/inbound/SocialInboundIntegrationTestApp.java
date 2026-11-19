package ru.posts.social.inbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import ru.posts.social.inbound.album.JdbcAlbumFragmentStore;
import ru.posts.social.inbound.dedup.InboundEventStore;

@SpringBootConfiguration
@EnableAutoConfiguration(
        exclude = {
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class,
            RabbitAutoConfiguration.class
        })
public class SocialInboundIntegrationTestApp {

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    InboundEventStore inboundEventStore(JdbcClient jdbc, ObjectMapper objectMapper) {
        return new InboundEventStore(jdbc, objectMapper);
    }

    @Bean
    JdbcAlbumFragmentStore jdbcAlbumFragmentStore(JdbcClient jdbc, ObjectMapper objectMapper) {
        return new JdbcAlbumFragmentStore(jdbc, objectMapper);
    }
}
