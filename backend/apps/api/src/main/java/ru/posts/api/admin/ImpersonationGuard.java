package ru.posts.api.admin;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.security.auth.PostsPrincipal;
import ru.posts.security.auth.SecurityAuthSupport;

/**
 * Blocks dangerous account/billing mutations while the caller holds an impersonation access token.
 */
@Component
public class ImpersonationGuard {

    public void forbidIfImpersonating(String action) {
        SecurityAuthSupport.currentPrincipal().ifPresent(principal -> forbidIfImpersonating(principal, action));
    }

    public void forbidIfImpersonating(PostsPrincipal principal, String action) {
        if (principal != null && principal.impersonating()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    ProblemCodes.IMPERSONATION_FORBIDDEN,
                    "Action '" + action + "' is forbidden while impersonating");
        }
    }
}
