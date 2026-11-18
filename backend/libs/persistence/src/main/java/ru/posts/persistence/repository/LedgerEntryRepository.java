package ru.posts.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.posts.persistence.entity.LedgerEntryEntity;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntryEntity, UUID> {
    List<LedgerEntryEntity> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    Optional<LedgerEntryEntity> findByIdempotencyKey(String idempotencyKey);

    @Query("select coalesce(sum(e.amountKopecks), 0) from LedgerEntryEntity e where e.workspaceId = :workspaceId")
    long sumAmountKopecksByWorkspaceId(@Param("workspaceId") UUID workspaceId);
}
