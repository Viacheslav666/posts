package ru.posts.notifications.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class NotificationsPropertiesBindingTest {

    @Test
    void bindsNestedTelegramProperties() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "posts.notifications.telegram.bot-token", "tok-123",
                "posts.notifications.telegram.api-base-url", "http://127.0.0.1:18080",
                "posts.notifications.mail.from", "alerts@posts.local"));

        NotificationsProperties bound = new Binder(source)
                .bind("posts.notifications", Bindable.of(NotificationsProperties.class))
                .get();

        assertThat(bound.telegram().botToken()).isEqualTo("tok-123");
        assertThat(bound.telegram().apiBaseUrl()).isEqualTo("http://127.0.0.1:18080");
        assertThat(bound.mail().from()).isEqualTo("alerts@posts.local");
    }
}
