package org.lucoenergia.conluz.domain.production;

import org.lucoenergia.conluz.infrastructure.production.EnergyStationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EnergyStationRepository extends JpaRepository<EnergyStationEntity, UUID> {

    List<EnergyStationEntity> findAllByInverterProvider(InverterProvider provider);
}
