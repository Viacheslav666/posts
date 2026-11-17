package ru.posts.social.api;

/** Successful (or recovered) remote publication reference. */
public record PublishResult(String remotePostId, String remoteUrl) {}
