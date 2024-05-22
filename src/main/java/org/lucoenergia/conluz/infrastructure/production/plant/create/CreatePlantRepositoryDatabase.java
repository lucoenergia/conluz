package org.lucoenergia.conluz.infrastructure.production.plant.create;

import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantAlreadyExistsException;
import org.lucoenergia.conluz.domain.production.plant.PlantCannotBeCreatedException;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.shared.PlantCode;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntityMapper;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional
@Repository
public class CreatePlantRepositoryDatabase implements CreatePlantRepository {

    private final SupplyRepository supplyRepository;
    private final PlantRepository plantRepository;
    private final SupplyEntityMapper supplyEntityMapper;
    private final PlantEntityMapper plantEntityMapper;

    public CreatePlantRepositoryDatabase(SupplyRepository supplyRepository, PlantRepository plantRepository,
                                         SupplyEntityMapper supplyEntityMapper, PlantEntityMapper plantEntityMapper) {
        this.supplyRepository = supplyRepository;
        this.plantRepository = plantRepository;
        this.supplyEntityMapper = supplyEntityMapper;
        this.plantEntityMapper = plantEntityMapper;
    }

    @Override
    public Plant create(Plant plant, SupplyId id) {
        Optional<SupplyEntity> result = supplyRepository.findById(id.getId());
        if (result.isEmpty()) {
            throw new SupplyNotFoundException(id);
        }
        if (plantRepository.countByCode(plant.getCode()) > 0) {
            throw new PlantAlreadyExistsException(PlantCode.of(plant.getCode()));
        }

        SupplyEntity supplyEntity = result.get();
        PlantEntity plantEntity = new PlantEntity.Builder()
                .withId(UUID.randomUUID())
                .withCode(plant.getCode())
                .withName(plant.getName())
                .withAddress(plant.getAddress())
                .withDescription(plant.getDescription())
                .withInverterProvider(plant.getInverterProvider())
                .withTotalPower(plant.getTotalPower())
                .withConnectionDate(plant.getConnectionDate())
                .build();

        supplyEntity.addPlant(plantEntity);

        supplyRepository.save(supplyEntity);

        Optional<PlantEntity> newPlantEntity = plantRepository.findByCode(plant.getCode());
        if (newPlantEntity.isEmpty()) {
            throw new PlantCannotBeCreatedException();
        }

        Plant newPlant = plantEntityMapper.map(newPlantEntity.get());
        newPlant.setSupply(supplyEntityMapper.map(supplyEntity));

        return newPlant;
    }
}
