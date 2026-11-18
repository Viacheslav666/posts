package ru.posts.content;

/** Structured validation finding for rendered content. */
public record ContentValidationIssue(String code, String severity, String message, String field) {}
