package org.lucoenergia.conluz.infrastructure.consumption.shelly;

import org.lucoenergia.conluz.infrastructure.consumption.shelly.config.ShellyConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ShellyConfigRepository extends JpaRepository<ShellyConfigEntity, UUID> {

    Optional<ShellyConfigEntity> findFirstByOrderByIdAsc();
}