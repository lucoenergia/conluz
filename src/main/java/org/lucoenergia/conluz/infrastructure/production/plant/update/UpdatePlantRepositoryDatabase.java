package org.lucoenergia.conluz.infrastructure.production.plant.update;

import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.SupplyAlreadyHasPlantException;
import org.lucoenergia.conluz.domain.production.plant.update.UpdatePlantRepository;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntityMapper;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantSupplyUniqueConstraint;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
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
        // The supply is resolved and checked first, so a refused move leaves the loaded plant untouched.
        if (!currentPlant.getSupply().getCode().equals(plant.getSupply().getCode())) {
            Optional<SupplyEntity> supplyEntityOptional = supplyRepository.findByCode(plant.getSupply().getCode());
            if (supplyEntityOptional.isEmpty()) {
                throw new SupplyNotFoundException(SupplyCode.of(plant.getSupply().getCode()));
            }
            SupplyEntity newSupply = supplyEntityOptional.get();
            if (repository.existsBySupplyId(newSupply.getId())) {
                throw new SupplyAlreadyHasPlantException(SupplyId.of(newSupply.getId()));
            }
            currentPlant.setSupply(newSupply);
        }

        currentPlant.setProviderCode(plant.getProviderCode());
        currentPlant.setRegulatoryCode(plant.getRegulatoryCode());
        currentPlant.setName(plant.getName());
        currentPlant.setDescription(plant.getDescription());
        currentPlant.setAddress(plant.getAddress());
        currentPlant.setTotalPower(plant.getTotalPower());
        currentPlant.setConnectionDate(plant.getConnectionDate());
        currentPlant.setInverterProvider(plant.getInverterProvider());

        try {
            // Flushed here so a concurrent plant on the same supply that passed the check above is
            // rejected by the unique constraint now, where it can be told apart from other integrity errors.
            return mapper.map(repository.saveAndFlush(currentPlant));
        } catch (DataIntegrityViolationException e) {
            throw PlantSupplyUniqueConstraint.translate(e, SupplyId.of(currentPlant.getSupply().getId()));
        }
    }
}
