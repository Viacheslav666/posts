package ru.posts.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.TelegramBotEntity;

public interface TelegramBotRepository extends JpaRepository<TelegramBotEntity, UUID> {
    List<TelegramBotEntity> findByStatusOrderByCreatedAtAsc(String status);

    Optional<TelegramBotEntity> findByChannelId(UUID channelId);

    List<TelegramBotEntity> findByKindAndStatus(String kind, String status);
}
