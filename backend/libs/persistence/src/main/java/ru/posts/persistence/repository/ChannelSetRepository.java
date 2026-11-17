package ru.posts.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.ChannelSetEntity;

public interface ChannelSetRepository extends JpaRepository<ChannelSetEntity, UUID> {
    List<ChannelSetEntity> findByWorkspaceId(UUID workspaceId);
}
