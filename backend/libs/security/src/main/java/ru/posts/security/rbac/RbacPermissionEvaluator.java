package ru.posts.security.rbac;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Pure role × action × scope/conditions evaluator (docs/product/roles-permissions.yaml).
 *
 * <p>Does not perform IDOR checks — callers must return 404 for foreign workspaces before invoking
 * this evaluator for same-workspace deny → 403.
 */
public final class RbacPermissionEvaluator {

    private final RbacMatrix matrix;

    public RbacPermissionEvaluator(RbacMatrix matrix) {
        this.matrix = matrix;
    }

    public RbacMatrix matrix() {
        return matrix;
    }

    public boolean isAllowed(RbacRole role, String action) {
        return isAllowed(role, action, RbacEvaluationContext.workspaceOnly());
    }

    public boolean isAllowed(RbacRole role, String action, RbacEvaluationContext ctx) {
        RbacMatrixEntry entry = matrix.find(action).orElse(null);
        if (entry == null) {
            return false;
        }
        if (entry.effectFor(role) != RbacEffect.allow) {
            return false;
        }
        if (!scopeAllows(role, entry.scope(), ctx)) {
            return false;
        }
        return conditionsAllow(entry.conditionsFor(role), ctx);
    }

    private static boolean scopeAllows(RbacRole role, RbacScope scope, RbacEvaluationContext ctx) {
        return switch (scope) {
            case workspace, billing, own_posts -> true;
            case assigned_projects -> {
                if (role.seesAllProjects()) {
                    yield true;
                }
                UUID projectId = ctx.projectId();
                if (projectId == null) {
                    // Listing / workspace-scoped call: allowed; filtering happens in services.
                    yield true;
                }
                Set<UUID> assigned = ctx.assignedProjectIds();
                yield assigned != null && assigned.contains(projectId);
            }
        };
    }

    private static boolean conditionsAllow(List<String> conditions, RbacEvaluationContext ctx) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        for (String condition : conditions) {
            switch (condition) {
                case "own_posts_only" -> {
                    if (ctx.resourceAuthorId() == null || ctx.actorUserId() == null) {
                        // No author context yet (e.g. create) — allow; update/delete must pass author.
                        continue;
                    }
                    if (!ctx.resourceAuthorId().equals(ctx.actorUserId())) {
                        return false;
                    }
                }
                case "client_approval_enabled" -> {
                    if (!ctx.clientApprovalEnabled()) {
                        return false;
                    }
                }
                default -> {
                    // Unknown condition → fail closed
                    return false;
                }
            }
        }
        return true;
    }
}
