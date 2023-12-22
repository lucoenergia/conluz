package org.lucoenergia.conluz.infrastructure.admin.config;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConfigRepository extends JpaRepository<ConfigEntity, UUID> {

    default Optional<ConfigEntity> find() {
        return findAll().stream().findFirst();
    }
}
