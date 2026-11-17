package ru.posts.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.posts.persistence.entity.SessionEntity;

public interface SessionRepository extends JpaRepository<SessionEntity, UUID> {
    Optional<SessionEntity> findByRefreshTokenHashAndRevokedAtIsNull(String refreshTokenHash);

    Optional<SessionEntity> findByRefreshTokenHash(String refreshTokenHash);

    List<SessionEntity> findByUserIdOrderByLastUsedAtDesc(UUID userId);

    List<SessionEntity> findByUserIdAndRevokedAtIsNull(UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update SessionEntity s set s.revokedAt = :revokedAt where s.familyId = :familyId and s.revokedAt is null")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("revokedAt") Instant revokedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update SessionEntity s set s.revokedAt = :revokedAt where s.userId = :userId and s.revokedAt is null")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "update SessionEntity s set s.revokedAt = :revokedAt where s.userId = :userId and s.id <> :keepSessionId and s.revokedAt is null")
    int revokeOtherSessions(
            @Param("userId") UUID userId,
            @Param("keepSessionId") UUID keepSessionId,
            @Param("revokedAt") Instant revokedAt);
}
