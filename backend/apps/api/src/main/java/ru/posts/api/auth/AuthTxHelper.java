package ru.posts.api.auth;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Commits side-effects that must survive a subsequent {@link ru.posts.api.error.ApiException}. */
@Component
public class AuthTxHelper {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void runInNewTx(Runnable action) {
        action.run();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> T callInNewTx(Supplier<T> action) {
        return action.get();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeFamily(SessionRevoker revoker, UUID familyId, Instant at) {
        revoker.revokeFamily(familyId, at);
    }

    @FunctionalInterface
    public interface SessionRevoker {
        void revokeFamily(UUID familyId, Instant at);
    }
}
