package ru.posts.grabber.ai;

import java.util.List;
import ru.posts.grabber.config.GrabberProperties;

/** Test/dev rewrite. Prefixes the text and charges the configured stub cost. No network calls. */
public final class StubGrabberRewriteProvider implements GrabberRewriteProvider {

    private final GrabberProperties properties;

    public StubGrabberRewriteProvider(GrabberProperties properties) {
        this.properties = properties;
    }

    @Override
    public String id() {
        return "stub";
    }

    @Override
    public List<String> models() {
        return List.of("stub");
    }

    @Override
    public boolean enabled() {
        return properties.getAi().isEnabled();
    }

    @Override
    public RewriteResult rewrite(RewriteRequest request) {
        String model = request.model() == null || request.model().isBlank()
                ? "stub"
                : request.model().trim();
        if (!models().contains(model)) {
            throw new IllegalArgumentException("unknown model: " + model);
        }
        long cost = properties.getAi().getCostKopecks();
        String body = request.text() == null ? "" : request.text();
        return new RewriteResult("[ai:" + model + "] " + body, Math.max(0L, cost), model);
    }
}
