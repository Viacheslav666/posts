package ru.posts.persistence.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.NotificationTelegramLinkEntity;

public interface NotificationTelegramLinkRepository extends JpaRepository<NotificationTelegramLinkEntity, UUID> {

    Optional<NotificationTelegramLinkEntity> findByLinkCodeHash(String linkCodeHash);

    Optional<NotificationTelegramLinkEntity> findByChatId(Long chatId);
}
