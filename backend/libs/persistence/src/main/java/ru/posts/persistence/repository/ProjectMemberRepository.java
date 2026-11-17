package ru.posts.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.ProjectMemberEntity;
import ru.posts.persistence.entity.ProjectMemberEntity.ProjectMemberId;

public interface ProjectMemberRepository extends JpaRepository<ProjectMemberEntity, ProjectMemberId> {
    List<ProjectMemberEntity> findByProjectId(UUID projectId);

    List<ProjectMemberEntity> findByUserId(UUID userId);

    List<ProjectMemberEntity> findByProjectIdInAndUserId(Collection<UUID> projectIds, UUID userId);

    Optional<ProjectMemberEntity> findByProjectIdAndUserId(UUID projectId, UUID userId);

    void deleteByProjectId(UUID projectId);

    void deleteByUserIdAndProjectIdIn(UUID userId, Collection<UUID> projectIds);
}
