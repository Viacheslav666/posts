package ru.posts.grabber.ai;

import java.util.List;

/**
 * Pluggable text rewrite. Implementations must not embed API keys; configuration comes from the
 * environment. The default stub is registered only when {@code posts.grabber.ai.enabled=true}.
 */
public interface GrabberRewriteProvider {

    String id();

    List<String> models();

    boolean enabled();

    RewriteResult rewrite(RewriteRequest request);

    record RewriteRequest(String text, String model, long costKopecksOverride) {}

    record RewriteResult(String text, long costKopecks, String model) {}
}
