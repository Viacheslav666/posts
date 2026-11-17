package ru.posts.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.UsageCounterEntity;
import ru.posts.persistence.entity.UsageCounterEntity.UsageCounterId;

public interface UsageCounterRepository extends JpaRepository<UsageCounterEntity, UsageCounterId> {}
