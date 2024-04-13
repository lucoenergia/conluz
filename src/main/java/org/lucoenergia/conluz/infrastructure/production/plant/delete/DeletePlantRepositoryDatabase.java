package org.lucoenergia.conluz.infrastructure.production.plant.delete;

import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.delete.DeletePlantRepository;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Transactional
@Repository
public class DeletePlantRepositoryDatabase implements DeletePlantRepository {

    private final PlantRepository plantRepository;

    public DeletePlantRepositoryDatabase(PlantRepository plantRepository) {
        this.plantRepository = plantRepository;
    }

    @Override
    public void delete(PlantId id) {
        Optional<PlantEntity> entity = plantRepository.findById(id.getId());
        if (entity.isEmpty()) {
            throw new PlantNotFoundException(id);
        }
        plantRepository.delete(entity.get());
    }
}
