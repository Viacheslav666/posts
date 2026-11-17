package ru.posts.security.rbac;

import java.util.Set;
import java.util.UUID;

/**
 * Runtime context for evaluating a permission beyond role × action.
 *
 * @param projectId project under access, if any
 * @param assignedProjectIds ProjectGrant set for the member (ignored for owner/admin)
 * @param resourceAuthorId post/media author for {@code own_posts_only}
 * @param actorUserId current user
 * @param clientApprovalEnabled project flag for viewer approvals
 */
public record RbacEvaluationContext(
        UUID projectId,
        Set<UUID> assignedProjectIds,
        UUID resourceAuthorId,
        UUID actorUserId,
        boolean clientApprovalEnabled) {

    public static RbacEvaluationContext workspaceOnly() {
        return new RbacEvaluationContext(null, Set.of(), null, null, false);
    }

    public static RbacEvaluationContext forProject(UUID projectId, Set<UUID> assignedProjectIds) {
        return new RbacEvaluationContext(projectId, assignedProjectIds, null, null, false);
    }
}
