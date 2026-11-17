package ru.posts.persistence.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.OauthLinkChallengeEntity;

public interface OauthLinkChallengeRepository extends JpaRepository<OauthLinkChallengeEntity, UUID> {
    Optional<OauthLinkChallengeEntity> findByTokenHashAndConsumedAtIsNull(String tokenHash);
}
