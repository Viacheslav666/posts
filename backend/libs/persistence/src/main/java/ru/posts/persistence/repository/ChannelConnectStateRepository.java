package ru.posts.persistence.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.posts.persistence.entity.ChannelConnectStateEntity;

public interface ChannelConnectStateRepository extends JpaRepository<ChannelConnectStateEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ChannelConnectStateEntity s where s.state = :state")
    Optional<ChannelConnectStateEntity> lockByState(@Param("state") String state);
}
