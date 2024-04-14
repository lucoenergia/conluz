package org.lucoenergia.conluz.infrastructure.production.get;

import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.lucoenergia.conluz.domain.production.InverterProvider;
import org.lucoenergia.conluz.domain.production.get.GetEnergyStationRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Transactional
@Repository
public class GetEnergyStationRepositoryDatabase implements GetEnergyStationRepository {

    private final PlantRepository plantRepository;

    public GetEnergyStationRepositoryDatabase(PlantRepository plantRepository) {
        this.plantRepository = plantRepository;
    }

    @Override
    public List<Plant> findAll() {
        List<PlantEntity> entities = plantRepository.findAll();
        return entities.stream()
                .map(this::mapEntityToDomain)
                .toList();
    }

    @Override
    public List<Plant> findAllByInverterProvider(InverterProvider provider) {
        List<PlantEntity> entities = plantRepository.findAllByInverterProvider(provider);
        return entities.stream()
                .map(this::mapEntityToDomain)
                .toList();
    }

    private Plant mapEntityToDomain(PlantEntity entity) {
        return new Plant.Builder()
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
