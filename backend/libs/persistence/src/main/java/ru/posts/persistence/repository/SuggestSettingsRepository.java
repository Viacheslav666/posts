package ru.posts.persistence.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.posts.persistence.entity.SuggestSettingsEntity;

public interface SuggestSettingsRepository extends JpaRepository<SuggestSettingsEntity, UUID> {

    Optional<SuggestSettingsEntity> findByChannelId(UUID channelId);

    Optional<SuggestSettingsEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<SuggestSettingsEntity> findByWorkspaceIdAndProjectIdOrderByCreatedAtDesc(UUID workspaceId, UUID projectId);

    @Query(
            """
            SELECT s FROM SuggestSettingsEntity s
            WHERE s.status = 'active'
              AND s.nextSyncAt IS NOT NULL
              AND s.nextSyncAt <= :now
            ORDER BY s.nextSyncAt ASC
            """)
    List<SuggestSettingsEntity> findDueForSync(@Param("now") Instant now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SuggestSettingsEntity s WHERE s.id = :id")
    Optional<SuggestSettingsEntity> findByIdForUpdate(@Param("id") UUID id);
}
