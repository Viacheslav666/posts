package ru.posts.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.ProjectMemberEntity;
import ru.posts.persistence.entity.ProjectMemberEntity.ProjectMemberId;

public interface ProjectMemberRepository extends JpaRepository<ProjectMemberEntity, ProjectMemberId> {
    List<ProjectMemberEntity> findByProjectId(UUID projectId);
}
