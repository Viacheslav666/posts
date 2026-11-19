package ru.posts.social.inbound.subscription;

import java.util.UUID;

/** Port for sealing/unsealing subscription secrets with workspace DEK. */
public interface SecretCrypto {
    byte[] seal(UUID workspaceId, String plaintext);

    String unseal(UUID workspaceId, byte[] blob);
}
