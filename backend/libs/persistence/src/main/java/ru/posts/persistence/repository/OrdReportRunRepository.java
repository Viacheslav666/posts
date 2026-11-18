package ru.posts.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.posts.persistence.entity.OrdReportRunEntity;

public interface OrdReportRunRepository extends JpaRepository<OrdReportRunEntity, UUID> {

    Optional<OrdReportRunEntity> findByWorkspaceIdAndProviderAndPeriodYearAndPeriodMonth(
            UUID workspaceId, String provider, int periodYear, int periodMonth);

    List<OrdReportRunEntity> findByStatus(String status);

    @Query(
            """
            select r from OrdReportRunEntity r
            where r.status = 'error'
              and r.nextRetryAt is not null
              and r.nextRetryAt <= :now
            """)
    List<OrdReportRunEntity> findDueRetries(@Param("now") Instant now);

    @Query(
            """
            select r from OrdReportRunEntity r
            where r.periodYear = :year
              and r.periodMonth = :month
              and r.status <> 'succeeded'
            """)
    List<OrdReportRunEntity> findIncompleteForPeriod(@Param("year") int year, @Param("month") int month);
}
