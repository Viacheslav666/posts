package ru.posts.ord.api;

/** Result of an ERID status check against an ORD provider. */
public record EridCheckResult(String erid, String status, String creativeExternalId, String rawStatus, boolean found) {}
