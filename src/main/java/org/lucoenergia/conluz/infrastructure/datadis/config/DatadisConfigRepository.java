package org.lucoenergia.conluz.infrastructure.datadis.config;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DatadisConfigRepository extends JpaRepository<DatadisConfigEntity, UUID> {

    Optional<DatadisConfigEntity> findFirstBy();

    Optional<DatadisConfigEntity> findByCommunityId(UUID communityId);

    java.util.List<DatadisConfigEntity> findAllByEnabledTrue();
}
