package ru.posts.content;

import java.util.List;

/** Rendered text for one network plus validation outcome. */
public record ContentRenderResult(
        String network,
        String html,
        String plainText,
        boolean valid,
        List<String> warnings,
        List<ContentValidationIssue> issues) {}
