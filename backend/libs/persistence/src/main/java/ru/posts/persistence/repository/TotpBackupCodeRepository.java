package ru.posts.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.TotpBackupCodeEntity;

public interface TotpBackupCodeRepository extends JpaRepository<TotpBackupCodeEntity, UUID> {
    List<TotpBackupCodeEntity> findByUserIdAndUsedAtIsNull(UUID userId);

    void deleteByUserId(UUID userId);
}
