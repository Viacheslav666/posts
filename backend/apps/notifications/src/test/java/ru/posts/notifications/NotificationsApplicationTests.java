package ru.posts.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NotificationsApplicationTests {

    @Test
    void applicationEntryPointIsPresent() {
        assertThat(NotificationsApplication.class.getName()).contains("NotificationsApplication");
    }
}
