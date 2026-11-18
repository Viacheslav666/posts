package ru.posts.notifications.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.posts.persistence.entity.NotificationSettingsEntity;

class PreferenceResolverTest {

    @Test
    void usesDefaultsWhenSettingsMissing() {
        ChannelPreference failed = PreferenceResolver.resolve(null, "publication.failed");
        assertThat(failed.email()).isTrue();
        assertThat(failed.telegram()).isTrue();
        assertThat(failed.inApp()).isTrue();

        ChannelPreference succeeded = PreferenceResolver.resolve(null, "publication.succeeded");
        assertThat(succeeded.email()).isFalse();
        assertThat(succeeded.inApp()).isTrue();
        assertThat(succeeded.ws()).isTrue();

        ChannelPreference invitation = PreferenceResolver.resolve(null, "invitation.created");
        assertThat(invitation.email()).isTrue();
        assertThat(invitation.telegram()).isFalse();
        assertThat(invitation.webPush()).isFalse();
    }

    @Test
    void respectsMutedTypes() {
        NotificationSettingsEntity settings = new NotificationSettingsEntity();
        settings.setUserId(UUID.randomUUID());
        settings.setPreferences(Map.of("mutedTypes", List.of("publication.failed")));
        ChannelPreference prefs = PreferenceResolver.resolve(settings, "publication.failed");
        assertThat(prefs.email()).isFalse();
        assertThat(prefs.inApp()).isFalse();
    }

    @Test
    void respectsEmailUnsubscribedAll() {
        NotificationSettingsEntity settings = new NotificationSettingsEntity();
        settings.setUserId(UUID.randomUUID());
        settings.setEmailUnsubscribedAll(true);
        ChannelPreference prefs = PreferenceResolver.resolve(settings, "publication.failed");
        assertThat(prefs.email()).isFalse();
        assertThat(prefs.telegram()).isTrue();
        assertThat(prefs.inApp()).isTrue();
    }

    @Test
    void overridesFromPreferencesMatrix() {
        NotificationSettingsEntity settings = new NotificationSettingsEntity();
        settings.setUserId(UUID.randomUUID());
        settings.setPreferences(Map.of(
                "publication.failed",
                Map.of("email", false, "telegram", true, "web_push", false, "in_app", true, "ws", false)));
        ChannelPreference prefs = PreferenceResolver.resolve(settings, "publication.failed");
        assertThat(prefs.email()).isFalse();
        assertThat(prefs.telegram()).isTrue();
        assertThat(prefs.webPush()).isFalse();
        assertThat(prefs.inApp()).isTrue();
        assertThat(prefs.ws()).isFalse();
    }
}
