package ru.posts.ord.reporting;

import java.util.UUID;

/** Unseals ORD connection API keys (ADR-0006). Implemented by apps with TokenCryptoService. */
@FunctionalInterface
public interface OrdApiKeyResolver {

    String resolve(UUID workspaceId, byte[] apiKeyEnc);
}
