package ru.posts.api.workspace;

import java.util.Set;
import java.util.UUID;
import ru.posts.security.rbac.RbacRole;

/** Resolved membership of the current user in a workspace. */
public record WorkspaceMembership(UUID workspaceId, UUID userId, RbacRole role, Set<UUID> assignedProjectIds) {}
