package ru.posts.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.posts.persistence.entity.OrdInvoiceEntity;

public interface OrdInvoiceRepository extends JpaRepository<OrdInvoiceEntity, UUID> {

    List<OrdInvoiceEntity> findByWorkspaceIdAndPeriodYearAndPeriodMonth(
            UUID workspaceId, int periodYear, int periodMonth);

    Optional<OrdInvoiceEntity> findByIdAndWorkspaceIdAndDeletedAtIsNull(UUID id, UUID workspaceId);

    Optional<OrdInvoiceEntity> findByWorkspaceIdAndProviderAndPeriodYearAndPeriodMonthAndExternalId(
            UUID workspaceId, String provider, int periodYear, int periodMonth, String externalId);

    @Query(
            """
            select i from OrdInvoiceEntity i
            where i.workspaceId = :workspaceId
              and i.deletedAt is null
              and (:year is null or i.periodYear = :year)
              and (:month is null or i.periodMonth = :month)
              and (:provider is null or i.provider = :provider)
              and (:status is null or i.status = :status)
            order by i.periodYear desc, i.periodMonth desc, i.createdAt desc
            """)
    List<OrdInvoiceEntity> searchActive(
            @Param("workspaceId") UUID workspaceId,
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("provider") String provider,
            @Param("status") String status);
}
