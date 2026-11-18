package ru.posts.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.posts.persistence.entity.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByEmailIgnoreCase(String email);

    @Query(
            """
            select u from UserEntity u
            where u.deletedAt is null
              and (:q is null or lower(u.email) like lower(concat('%', cast(:q as string), '%'))
                   or lower(u.name) like lower(concat('%', cast(:q as string), '%')))
              and (:platformAdmin is null or u.platformAdmin = :platformAdmin)
            order by u.createdAt desc, u.id desc
            """)
    List<UserEntity> search(@Param("q") String q, @Param("platformAdmin") Boolean platformAdmin);
}
