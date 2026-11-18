package ru.posts.persistence.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.ReferralEntity;

public interface ReferralRepository extends JpaRepository<ReferralEntity, UUID> {}
