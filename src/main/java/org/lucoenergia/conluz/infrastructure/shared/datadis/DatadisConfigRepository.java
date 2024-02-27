package org.lucoenergia.conluz.infrastructure.shared.datadis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DatadisConfigRepository extends JpaRepository<DatadisConfig, UUID> {

    DatadisConfig findFirstByOrderByIdAsc();
}
