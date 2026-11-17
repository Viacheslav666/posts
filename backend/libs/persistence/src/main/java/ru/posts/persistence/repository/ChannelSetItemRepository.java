package ru.posts.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.ChannelSetItemEntity;
import ru.posts.persistence.entity.ChannelSetItemEntity.ChannelSetItemId;

public interface ChannelSetItemRepository extends JpaRepository<ChannelSetItemEntity, ChannelSetItemId> {
    List<ChannelSetItemEntity> findBySetId(UUID setId);
}
