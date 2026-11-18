package ru.posts.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.OrdInvoiceEntity;

public interface OrdInvoiceRepository extends JpaRepository<OrdInvoiceEntity, UUID> {

    List<OrdInvoiceEntity> findByWorkspaceIdAndPeriodYearAndPeriodMonth(
            UUID workspaceId, int periodYear, int periodMonth);
}
