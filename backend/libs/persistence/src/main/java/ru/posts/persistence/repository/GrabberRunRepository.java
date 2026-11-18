package ru.posts.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.GrabberRunEntity;

public interface GrabberRunRepository extends JpaRepository<GrabberRunEntity, UUID> {

    List<GrabberRunEntity> findByRuleIdOrderByStartedAtDesc(UUID ruleId);

    Optional<GrabberRunEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    long countByRuleIdAndTriggerAndStartedAtAfter(UUID ruleId, String trigger, Instant after);
}
