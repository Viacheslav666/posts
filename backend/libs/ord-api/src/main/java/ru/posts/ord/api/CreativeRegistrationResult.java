package ru.posts.ord.api;

/** Result of creative registration / upsert (erid = Yandex {@code token}). */
public record CreativeRegistrationResult(String externalId, String erid, String providerRef) {}
