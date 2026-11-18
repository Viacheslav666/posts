package ru.posts.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.OrdStatisticsEntity;

public interface OrdStatisticsRepository extends JpaRepository<OrdStatisticsEntity, UUID> {

    List<OrdStatisticsEntity> findByWorkspaceIdAndPeriodYearAndPeriodMonth(
            UUID workspaceId, int periodYear, int periodMonth);
}
