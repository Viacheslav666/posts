package ru.posts.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.ScheduledActionEntity;
import ru.posts.persistence.entity.ScheduledActionId;

public interface ScheduledActionRepository extends JpaRepository<ScheduledActionEntity, ScheduledActionId> {
    List<ScheduledActionEntity> findByPublicationId(UUID publicationId);
}
