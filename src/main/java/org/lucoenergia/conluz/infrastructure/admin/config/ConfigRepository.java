package org.lucoenergia.conluz.infrastructure.admin.config;

import org.lucoenergia.conluz.domain.admin.config.init.ApplicationNotInitializedException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConfigRepository extends JpaRepository<ConfigEntity, UUID> {

    default ConfigEntity find() {
        return findAll().stream()
                .findFirst()
                .orElseThrow(ApplicationNotInitializedException::new);
    }
}
