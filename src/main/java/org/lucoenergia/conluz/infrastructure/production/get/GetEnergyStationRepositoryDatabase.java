package org.lucoenergia.conluz.infrastructure.production.get;

import org.lucoenergia.conluz.domain.production.EnergyStation;
import org.lucoenergia.conluz.domain.production.EnergyStationRepository;
import org.lucoenergia.conluz.domain.production.InverterProvider;
import org.lucoenergia.conluz.domain.production.get.GetEnergyStationRepository;
import org.lucoenergia.conluz.infrastructure.production.EnergyStationEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Transactional
@Repository
public class GetEnergyStationRepositoryDatabase implements GetEnergyStationRepository {

    private final EnergyStationRepository energyStationRepository;

    public GetEnergyStationRepositoryDatabase(EnergyStationRepository energyStationRepository) {
        this.energyStationRepository = energyStationRepository;
    }

    @Override
    public List<EnergyStation> findAll() {
        List<EnergyStationEntity> entities = energyStationRepository.findAll();
        return entities.stream()
                .map(this::mapEntityToDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<EnergyStation> findAllByInverterProvider(InverterProvider provider) {
        List<EnergyStationEntity> entities = energyStationRepository.findAllByInverterProvider(provider);
        return entities.stream()
                .map(this::mapEntityToDomain)
                .collect(Collectors.toList());
    }

    private EnergyStation mapEntityToDomain(EnergyStationEntity entity) {
        return new EnergyStation.Builder()
                .withId(entity.getId())
                .withName(entity.getName())
                .withCode(entity.getCode())
                .withAddress(entity.getAddress())
                .withDescription(entity.getDescription())
                .withInverterProvider(entity.getInverterProvider())
                .withTotalPower(entity.getTotalPower())
                .withConnectionDate(entity.getConnectionDate())
                .build();
    }
}
