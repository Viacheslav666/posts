package ru.posts.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.FeatureFlagEntity;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlagEntity, UUID> {
    Optional<FeatureFlagEntity> findByKey(String key);

    List<FeatureFlagEntity> findAllByOrderByKeyAsc();

    boolean existsByKey(String key);
}
