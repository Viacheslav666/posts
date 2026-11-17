package ru.posts.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.PublicationAttemptEntity;

public interface PublicationAttemptRepository extends JpaRepository<PublicationAttemptEntity, UUID> {
    List<PublicationAttemptEntity> findByPublicationIdOrderByAttemptNoAsc(UUID publicationId);
}
