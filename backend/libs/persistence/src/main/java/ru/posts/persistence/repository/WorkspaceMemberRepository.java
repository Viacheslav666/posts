package ru.posts.persistence.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.WorkspaceMemberEntity;
import ru.posts.persistence.entity.WorkspaceMemberEntity.WorkspaceMemberId;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMemberEntity, WorkspaceMemberId> {
    List<WorkspaceMemberEntity> findByWorkspaceId(java.util.UUID workspaceId);

    List<WorkspaceMemberEntity> findByUserId(java.util.UUID userId);
}
