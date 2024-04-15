package org.lucoenergia.conluz.infrastructure.consumption.datadis;

import org.lucoenergia.conluz.infrastructure.consumption.datadis.config.DatadisConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DatadisConfigRepository extends JpaRepository<DatadisConfigEntity, UUID> {

    Optional<DatadisConfigEntity> findFirstByOrderByIdAsc();
}
