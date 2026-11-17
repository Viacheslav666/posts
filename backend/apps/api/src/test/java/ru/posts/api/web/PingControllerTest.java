package ru.posts.api.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PingControllerTest {

    @Test
    void pingReturnsOk() {
        Map<String, String> body = new PingController().ping();
        assertThat(body).containsEntry("status", "ok");
    }
}
