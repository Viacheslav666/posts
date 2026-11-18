package ru.posts.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.AnalyticsPostsMonthlyEntity;

public interface AnalyticsPostsMonthlyRepository
        extends JpaRepository<AnalyticsPostsMonthlyEntity, AnalyticsPostsMonthlyEntity.Pk> {

    List<AnalyticsPostsMonthlyEntity> findByWorkspaceIdAndYearOrderByMonthAsc(UUID workspaceId, int year);
}
