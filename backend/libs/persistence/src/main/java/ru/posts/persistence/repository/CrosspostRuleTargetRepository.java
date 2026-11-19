package ru.posts.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.CrosspostRuleTargetEntity;

public interface CrosspostRuleTargetRepository extends JpaRepository<CrosspostRuleTargetEntity, UUID> {

    List<CrosspostRuleTargetEntity> findByRuleIdOrderByCreatedAtAsc(UUID ruleId);

    List<CrosspostRuleTargetEntity> findByRuleIdIn(Collection<UUID> ruleIds);

    Optional<CrosspostRuleTargetEntity> findByRuleIdAndTargetChannelId(UUID ruleId, UUID targetChannelId);

    void deleteByRuleId(UUID ruleId);

    void deleteByRuleIdAndTargetChannelId(UUID ruleId, UUID targetChannelId);

    long countByRuleId(UUID ruleId);
}
