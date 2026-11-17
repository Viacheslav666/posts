package ru.posts.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.PostVariantEntity;

public interface PostVariantRepository extends JpaRepository<PostVariantEntity, UUID> {
    List<PostVariantEntity> findByPostId(UUID postId);

    Optional<PostVariantEntity> findByPostIdAndChannelId(UUID postId, UUID channelId);
}
