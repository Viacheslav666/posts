package ru.posts.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.posts.persistence.entity.OrdStatisticsEntity;

public interface OrdStatisticsRepository extends JpaRepository<OrdStatisticsEntity, UUID> {

    List<OrdStatisticsEntity> findByWorkspaceIdAndPeriodYearAndPeriodMonth(
            UUID workspaceId, int periodYear, int periodMonth);

    Optional<OrdStatisticsEntity> findByWorkspaceIdAndCreativeIdAndPeriodYearAndPeriodMonthAndPlatformId(
            UUID workspaceId, UUID creativeId, int periodYear, int periodMonth, UUID platformId);

    @Query(
            """
            select s from OrdStatisticsEntity s
            where s.workspaceId = :workspaceId
              and (:year is null or s.periodYear = :year)
              and (:month is null or s.periodMonth = :month)
              and (:provider is null or s.provider = :provider)
              and (:status is null or s.status = :status)
              and (:creativeId is null or s.creativeId = :creativeId)
            order by s.periodYear desc, s.periodMonth desc, s.createdAt desc
            """)
    List<OrdStatisticsEntity> search(
            @Param("workspaceId") UUID workspaceId,
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("provider") String provider,
            @Param("status") String status,
            @Param("creativeId") UUID creativeId);
}
