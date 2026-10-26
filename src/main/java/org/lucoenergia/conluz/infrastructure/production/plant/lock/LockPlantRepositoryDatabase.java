package org.lucoenergia.conluz.infrastructure.production.plant.lock;

import org.lucoenergia.conluz.domain.production.plant.lock.LockPlantRepository;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class LockPlantRepositoryDatabase implements LockPlantRepository {

    private final PlantRepository repository;

    public LockPlantRepositoryDatabase(PlantRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean lock(PlantId plantId) {
        return repository.lockById(plantId.getId()).isPresent();
    }

    @Override
    public boolean lockAsReferenced(PlantId plantId) {
        return repository.lockAsReferencedById(plantId.getId()).isPresent();
    }
}
