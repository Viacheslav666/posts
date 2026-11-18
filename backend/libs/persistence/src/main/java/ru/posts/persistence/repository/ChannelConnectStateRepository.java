package ru.posts.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.ChannelConnectStateEntity;

public interface ChannelConnectStateRepository extends JpaRepository<ChannelConnectStateEntity, String> {}
