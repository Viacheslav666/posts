package ru.posts.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SchedulerApplicationTests {

    @Test
    void applicationEntryPointIsPresent() {
        assertThat(SchedulerApplication.class.getName()).contains("SchedulerApplication");
    }
}
