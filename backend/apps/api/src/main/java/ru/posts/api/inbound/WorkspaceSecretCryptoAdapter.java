package ru.posts.api.inbound;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import ru.posts.api.channel.WorkspaceTokenVault;
import ru.posts.social.inbound.subscription.SecretCrypto;

@Component
@Profile("!openapi")
public class WorkspaceSecretCryptoAdapter implements SecretCrypto {

    private final WorkspaceTokenVault vault;

    public WorkspaceSecretCryptoAdapter(WorkspaceTokenVault vault) {
        this.vault = vault;
    }

    @Override
    public byte[] seal(UUID workspaceId, String plaintext) {
        return vault.seal(workspaceId, plaintext);
    }

    @Override
    public String unseal(UUID workspaceId, byte[] blob) {
        return vault.unseal(workspaceId, blob);
    }
}
