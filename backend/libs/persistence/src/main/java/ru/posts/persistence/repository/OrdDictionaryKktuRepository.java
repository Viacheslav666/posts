package ru.posts.persistence.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.OrdDictionaryKktuEntity;

public interface OrdDictionaryKktuRepository extends JpaRepository<OrdDictionaryKktuEntity, String> {

    List<OrdDictionaryKktuEntity> findByCodeContainingIgnoreCase(String code);

    List<OrdDictionaryKktuEntity> findByNameContainingIgnoreCase(String name);
}
