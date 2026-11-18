package ru.posts.persistence.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.posts.persistence.entity.WalletEntity;

public interface WalletRepository extends JpaRepository<WalletEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WalletEntity w where w.workspaceId = :workspaceId")
    Optional<WalletEntity> findByIdForUpdate(@Param("workspaceId") UUID workspaceId);
}
