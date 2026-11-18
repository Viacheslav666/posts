package ru.posts.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.OrdContractorEntity;

public interface OrdContractorRepository extends JpaRepository<OrdContractorEntity, UUID> {

    List<OrdContractorEntity> findByWorkspaceIdOrderByUpdatedAtDesc(UUID workspaceId);

    Optional<OrdContractorEntity> findByWorkspaceIdAndInnAndKpp(UUID workspaceId, String inn, String kpp);

    Optional<OrdContractorEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
