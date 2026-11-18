package ru.posts.persistence.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.posts.persistence.entity.OrdDictionaryOksmEntity;

public interface OrdDictionaryOksmRepository extends JpaRepository<OrdDictionaryOksmEntity, String> {

    List<OrdDictionaryOksmEntity> findByCodeContainingIgnoreCase(String code);

    List<OrdDictionaryOksmEntity> findByNameContainingIgnoreCase(String name);
}
