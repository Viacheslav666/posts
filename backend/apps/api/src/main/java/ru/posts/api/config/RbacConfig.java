package ru.posts.api.config;

import java.io.Serializable;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import ru.posts.security.rbac.RbacEvaluationContext;
import ru.posts.security.rbac.RbacMatrix;
import ru.posts.security.rbac.RbacMatrixLoader;
import ru.posts.security.rbac.RbacPermissionEvaluator;
import ru.posts.security.rbac.RbacRole;

/**
 * Wires the YAML RBAC matrix and a Spring {@link PermissionEvaluator}.
 *
 * <p>HTTP handlers still enforce IDOR (404) via {@code WorkspaceAccessService}; this evaluator is
 * used by {@code @PreAuthorize("hasPermission(...)")} and matrix unit/integration tests.
 */
@Configuration
public class RbacConfig {

    @Bean
    RbacMatrix rbacMatrix() {
        return RbacMatrixLoader.loadDefault();
    }

    @Bean
    RbacPermissionEvaluator rbacPermissionEvaluator(RbacMatrix matrix) {
        return new RbacPermissionEvaluator(matrix);
    }

    @Bean
    PermissionEvaluator permissionEvaluator(RbacPermissionEvaluator rbac) {
        return new PermissionEvaluator() {
            @Override
            public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
                if (!(permission instanceof String action)) {
                    return false;
                }
                RbacRole role = extractRole(targetDomainObject);
                if (role == null) {
                    return false;
                }
                return rbac.isAllowed(role, action, RbacEvaluationContext.workspaceOnly());
            }

            @Override
            public boolean hasPermission(
                    Authentication authentication, Serializable targetId, String targetType, Object permission) {
                // targetId may be workspace UUID; without membership lookup we cannot decide — deny.
                // Controllers use WorkspaceAccessService for authoritative checks.
                if (!(permission instanceof String action) || !(targetId instanceof UUID)) {
                    return false;
                }
                if (!(authentication instanceof JwtAuthenticationToken)) {
                    return false;
                }
                // Allow expression parsing / openapi boot; real checks are in services.
                return "workspace".equals(targetType) && "workspace.view".equals(action);
            }

            private RbacRole extractRole(Object target) {
                if (target instanceof RbacRole role) {
                    return role;
                }
                if (target instanceof String s) {
                    try {
                        return RbacRole.from(s);
                    } catch (RuntimeException ex) {
                        return null;
                    }
                }
                return null;
            }
        };
    }
}
