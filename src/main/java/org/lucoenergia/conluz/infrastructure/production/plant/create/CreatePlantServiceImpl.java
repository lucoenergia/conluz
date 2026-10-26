package org.lucoenergia.conluz.infrastructure.production.plant.create;


import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.lock.LockSupplyRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantService;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.function.Supplier;

@Transactional
@Service
public class CreatePlantServiceImpl implements CreatePlantService {

    private final CreatePlantRepository repository;
    private final GetSupplyRepository getSupplyRepository;
    private final LockSupplyRepository lockSupplyRepository;

    public CreatePlantServiceImpl(CreatePlantRepository repository, GetSupplyRepository getSupplyRepository,
                                  LockSupplyRepository lockSupplyRepository) {
        this.repository = repository;
        this.getSupplyRepository = getSupplyRepository;
        this.lockSupplyRepository = lockSupplyRepository;
    }

    /**
     * The supply is locked against deletion before the plant is written (SUP-001): a deletion in progress is waited
     * for, and then the supply is not found, instead of the plant failing on its foreign key.
     */
    public Plant create(Plant plant, SupplyId id) {
        return createLocked(plant, id, () -> new SupplyNotFoundException(id));
    }

    public Plant create(Plant plant, SupplyCode code) {
        Optional<Supply> supply = getSupplyRepository.findByCode(code);
        if (supply.isEmpty()) {
            throw new SupplyNotFoundException(code);
        }
        return createLocked(plant, SupplyId.of(supply.get().getId()), () -> new SupplyNotFoundException(code));
    }

    private Plant createLocked(Plant plant, SupplyId id, Supplier<SupplyNotFoundException> notFound) {
        if (!lockSupplyRepository.lockAsReferenced(id)) {
            throw notFound.get();
        }
        plant.initializeUuid();
        return repository.create(plant, id);
    }
}
