package org.lucoenergia.conluz.infrastructure.production.plant.update;

import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.update.UpdatePlantRepository;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntityMapper;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class UpdatePlantRepositoryDatabase implements UpdatePlantRepository {

    private final PlantRepository repository;
    private final SupplyRepository supplyRepository;
    private final PlantEntityMapper mapper;

    public UpdatePlantRepositoryDatabase(PlantRepository repository, SupplyRepository supplyRepository,
                                         PlantEntityMapper mapper) {
        this.repository = repository;
        this.supplyRepository = supplyRepository;
        this.mapper = mapper;
    }

    @Override
    public Plant update(Plant plant) {
        UUID plantId = plant.getId();
        Optional<PlantEntity> result = repository.findById(plantId);
        if (result.isEmpty()) {
            throw new PlantNotFoundException(PlantId.of(plantId));
        }
        PlantEntity currentPlant = result.get();
        currentPlant.setCode(plant.getCode());
        currentPlant.setName(plant.getName());
        currentPlant.setDescription(plant.getDescription());
        currentPlant.setAddress(plant.getAddress());
        currentPlant.setTotalPower(plant.getTotalPower());
        currentPlant.setConnectionDate(plant.getConnectionDate());
        currentPlant.setInverterProvider(plant.getInverterProvider());
        if (!currentPlant.getSupply().getCode().equals(plant.getSupply().getCode())) {
            Optional<SupplyEntity> supplyEntityOptional = supplyRepository.findByCode(plant.getSupply().getCode());
            if (supplyEntityOptional.isEmpty()) {
                throw new SupplyNotFoundException(SupplyCode.of(plant.getSupply().getCode()));
            }
            currentPlant.setSupply(supplyEntityOptional.get());
        }

        return mapper.map(repository.save(currentPlant));
    }
}
