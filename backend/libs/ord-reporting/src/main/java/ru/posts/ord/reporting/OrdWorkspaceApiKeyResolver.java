package ru.posts.ord.reporting;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import ru.posts.persistence.entity.WorkspaceDataKeyEntity;
import ru.posts.persistence.repository.WorkspaceDataKeyRepository;
import ru.posts.security.crypto.TokenCryptoService;

/** Default ADR-0006 ORD API key unseal using workspace DEK. */
@Profile("!openapi")
@Component
@ConditionalOnBean(TokenCryptoService.class)
public class OrdWorkspaceApiKeyResolver implements OrdApiKeyResolver {

    private final TokenCryptoService crypto;
    private final WorkspaceDataKeyRepository dataKeyRepository;

    public OrdWorkspaceApiKeyResolver(TokenCryptoService crypto, WorkspaceDataKeyRepository dataKeyRepository) {
        this.crypto = crypto;
        this.dataKeyRepository = dataKeyRepository;
    }

    @Override
    public String resolve(UUID workspaceId, byte[] apiKeyEnc) {
        if (apiKeyEnc == null || apiKeyEnc.length == 0) {
            return null;
        }
        if (apiKeyEnc.length == 1 && apiKeyEnc[0] == 0) {
            return null;
        }
        WorkspaceDataKeyEntity row = dataKeyRepository
                .findById(workspaceId)
                .orElseThrow(() -> new IllegalStateException("Missing workspace DEK for " + workspaceId));
        byte[] dek = crypto.unwrapDek(row.getDekEnc());
        return crypto.unseal(dek, apiKeyEnc);
    }
}
