package ru.posts.api.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "System")
public class PingController {

    @GetMapping("/ping")
    @Operation(
            operationId = "ping",
            summary = "Liveness ping for local and CI smoke checks",
            description = "Unauthenticated smoke endpoint used by Makefile/CI.")
    public Map<String, String> ping() {
        return Map.of("status", "ok");
    }
}
