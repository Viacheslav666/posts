package ru.posts.persistence.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.OutboxEntity;

public interface OutboxRepository extends JpaRepository<OutboxEntity, Long> {
    List<OutboxEntity> findByPublishedAtIsNullOrderByIdAsc();
}
