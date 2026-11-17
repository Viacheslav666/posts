package ru.posts.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.WorkspaceMemberEntity;
import ru.posts.persistence.entity.WorkspaceMemberEntity.WorkspaceMemberId;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMemberEntity, WorkspaceMemberId> {
    List<WorkspaceMemberEntity> findByWorkspaceId(UUID workspaceId);

    List<WorkspaceMemberEntity> findByUserId(UUID userId);

    Optional<WorkspaceMemberEntity> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    long countByWorkspaceId(UUID workspaceId);

    void deleteByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);
}
