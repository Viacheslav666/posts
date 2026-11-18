package ru.posts.emulator.control;

import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.posts.emulator.store.EmulatedPost;
import ru.posts.emulator.store.Network;
import ru.posts.emulator.store.PostStore;

@RestController
@RequestMapping("/_control")
public class ControlController {

    private final ScenarioService scenarioService;
    private final PostStore postStore;

    public ControlController(ScenarioService scenarioService, PostStore postStore) {
        this.scenarioService = scenarioService;
        this.postStore = postStore;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "social-emulator");
    }

    @GetMapping("/scenario")
    public ScenarioConfig getScenario() {
        return scenarioService.get();
    }

    @PutMapping("/scenario")
    public ScenarioConfig putScenario(@Valid @RequestBody ScenarioConfig config) {
        return scenarioService.update(config);
    }

    @PostMapping("/reset")
    public Map<String, Object> reset(@RequestParam(name = "scenario", defaultValue = "true") boolean resetScenario) {
        postStore.reset();
        if (resetScenario) {
            scenarioService.reset();
        }
        return Map.of("reset", true, "scenarioReset", resetScenario, "counts", postStore.counts());
    }

    @GetMapping("/posts")
    public List<EmulatedPost> posts(
            @RequestParam(required = false) String network,
            @RequestParam(required = false) String channelId,
            @RequestParam(required = false) String contentType) {
        Network parsed = network == null || network.isBlank() ? null : Network.from(network);
        List<EmulatedPost> posts = postStore.list(parsed, channelId);
        if (contentType == null || contentType.isBlank()) {
            return posts;
        }
        boolean wantStory = "story".equalsIgnoreCase(contentType);
        return posts.stream()
                .filter(p -> {
                    Object ct = p.metadata() == null ? null : p.metadata().get("contentType");
                    boolean isStory = "story".equals(ct);
                    return wantStory == isStory;
                })
                .toList();
    }

    @GetMapping("/posts/count")
    public Map<String, Long> counts() {
        return postStore.counts();
    }

    @GetMapping("/duplicates")
    public Map<String, Object> duplicates() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("duplicateAttemptsByNetwork", postStore.duplicateAttemptCounts());
        body.put("trueDuplicates", postStore.findDuplicates());
        body.put("ok", postStore.findDuplicates().isEmpty());
        return body;
    }

    @GetMapping("/verify")
    public PostStore.VerificationReport verify() {
        return postStore.verify();
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("counts", postStore.counts());
        body.put("duplicateAttemptsByNetwork", postStore.duplicateAttemptCounts());
        body.put("scenario", scenarioService.get());
        return ResponseEntity.ok(body);
    }
}
