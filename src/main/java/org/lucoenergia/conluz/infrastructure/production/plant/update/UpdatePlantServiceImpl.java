package org.lucoenergia.conluz.infrastructure.production.plant.update;


import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.lock.LockSupplyRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.production.plant.update.UpdatePlantRepository;
import org.lucoenergia.conluz.domain.production.plant.update.UpdatePlantService;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
public class UpdatePlantServiceImpl implements UpdatePlantService {

    private final UpdatePlantRepository repository;
    private final GetPlantRepository getPlantRepository;
    private final GetSupplyRepository getSupplyRepository;
    private final LockSupplyRepository lockSupplyRepository;

    public UpdatePlantServiceImpl(UpdatePlantRepository repository, GetPlantRepository getPlantRepository,
                                  GetSupplyRepository getSupplyRepository, LockSupplyRepository lockSupplyRepository) {
        this.repository = repository;
        this.getPlantRepository = getPlantRepository;
        this.getSupplyRepository = getSupplyRepository;
        this.lockSupplyRepository = lockSupplyRepository;
    }

    /**
     * A plant moved to another supply locks that supply against deletion before it is written (SUP-001): a deletion
     * in progress is waited for, and then the supply is not found, instead of the plant failing on its foreign key.
     * The plant is checked first, as the update checks it before the supply.
     */
    public Plant update(Plant plant) {
        PlantId plantId = PlantId.of(plant.getId());
        Plant current = getPlantRepository.findById(plantId).orElseThrow(() -> new PlantNotFoundException(plantId));
        String supplyCode = plant.getSupply().getCode();
        if (!current.getSupply().getCode().equals(supplyCode)) {
            Supply supply = getSupplyRepository.findByCode(SupplyCode.of(supplyCode))
                    .orElseThrow(() -> new SupplyNotFoundException(SupplyCode.of(supplyCode)));
            if (!lockSupplyRepository.lockAsReferenced(SupplyId.of(supply.getId()))) {
                throw new SupplyNotFoundException(SupplyCode.of(supplyCode));
            }
        }
        return repository.update(plant);
    }
}
