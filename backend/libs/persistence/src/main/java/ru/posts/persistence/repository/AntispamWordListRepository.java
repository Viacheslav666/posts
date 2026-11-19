package ru.posts.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.AntispamWordListEntity;

public interface AntispamWordListRepository extends JpaRepository<AntispamWordListEntity, UUID> {

    Optional<AntispamWordListEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<AntispamWordListEntity> findByWorkspaceIdOrderByNameAsc(UUID workspaceId);

    List<AntispamWordListEntity> findByWorkspaceIdAndIdIn(UUID workspaceId, Collection<UUID> ids);
}
