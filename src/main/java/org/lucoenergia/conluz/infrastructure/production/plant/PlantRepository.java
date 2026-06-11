package org.lucoenergia.conluz.infrastructure.production.plant;

import org.lucoenergia.conluz.domain.production.InverterProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlantRepository extends JpaRepository<PlantEntity, UUID> {

    List<PlantEntity> findAllByInverterProvider(InverterProvider provider);

    int countByCode(String code);

    Optional<PlantEntity> findByCode(String code);

    /**
     * Plants whose supply belongs to any of the given communities. An empty collection yields an empty page.
     */
    Page<PlantEntity> findBySupply_Community_IdIn(Collection<UUID> communityIds, Pageable pageable);
}
