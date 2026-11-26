package ru.posts.api.channel;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.persistence.entity.WorkspaceDataKeyEntity;
import ru.posts.persistence.repository.WorkspaceDataKeyRepository;
import ru.posts.security.crypto.TokenCryptoService;

/** Loads / creates per-workspace DEKs and seals social tokens (ADR-0006). */
@Service
@Profile("!openapi")
public class WorkspaceTokenVault {

    private final TokenCryptoService crypto;
    private final WorkspaceDataKeyRepository dataKeyRepository;

    public WorkspaceTokenVault(TokenCryptoService crypto, WorkspaceDataKeyRepository dataKeyRepository) {
        this.crypto = crypto;
        this.dataKeyRepository = dataKeyRepository;
    }

    @Transactional
    public byte[] seal(UUID workspaceId, String plaintext) {
        byte[] dek = dekFor(workspaceId);
        return crypto.seal(dek, plaintext);
    }

    @Transactional(readOnly = true)
    public String unseal(UUID workspaceId, byte[] blob) {
        return doUnseal(workspaceId, blob);
    }

    /**
     * Same as {@link #unseal} but swallows failures. The catch stays inside this transactional
     * boundary so a missing DEK does not mark the caller's transaction rollback-only.
     */
    @Transactional(readOnly = true)
    public Optional<String> tryUnseal(UUID workspaceId, byte[] blob) {
        try {
            return Optional.ofNullable(doUnseal(workspaceId, blob));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private String doUnseal(UUID workspaceId, byte[] blob) {
        if (blob == null || blob.length == 0) {
            return null;
        }
        // Placeholder single-byte seed from migration — treat as empty.
        if (blob.length == 1 && blob[0] == 0) {
            return null;
        }
        byte[] dek = dekForRead(workspaceId);
        return crypto.unseal(dek, blob);
    }

    @Transactional
    public byte[] dekFor(UUID workspaceId) {
        return dataKeyRepository
                .findById(workspaceId)
                .map(row -> crypto.unwrapDek(row.getDekEnc()))
                .orElseGet(() -> createDek(workspaceId));
    }

    private byte[] dekForRead(UUID workspaceId) {
        WorkspaceDataKeyEntity row = dataKeyRepository
                .findById(workspaceId)
                .orElseThrow(() -> new IllegalStateException("Missing workspace DEK for " + workspaceId));
        return crypto.unwrapDek(row.getDekEnc());
    }

    private byte[] createDek(UUID workspaceId) {
        byte[] dek = crypto.generateDek();
        WorkspaceDataKeyEntity row = new WorkspaceDataKeyEntity();
        row.setWorkspaceId(workspaceId);
        row.setDekEnc(crypto.wrapDek(dek));
        row.setKeyVersion(1);
        row.setCreatedAt(Instant.now());
        dataKeyRepository.save(row);
        return dek;
    }
}
