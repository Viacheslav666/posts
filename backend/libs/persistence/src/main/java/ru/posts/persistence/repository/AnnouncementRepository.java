package ru.posts.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.posts.persistence.entity.AnnouncementEntity;

public interface AnnouncementRepository extends JpaRepository<AnnouncementEntity, UUID> {

    List<AnnouncementEntity> findAllByOrderByCreatedAtDesc();

    @Query(
            """
            select a from AnnouncementEntity a
            where a.active = true
              and a.startsAt <= :now
              and (a.endsAt is null or a.endsAt > :now)
            order by a.startsAt desc
            """)
    List<AnnouncementEntity> findActiveAt(@Param("now") Instant now);
}
