package org.lucoenergia.conluz.domain.production.plant.delete;

import org.lucoenergia.conluz.domain.shared.PlantId;
import org.springframework.stereotype.Service;

@Service
public class DeletePlantService {

    private final DeletePlantRepository repository;

    public DeletePlantService(DeletePlantRepository repository) {
        this.repository = repository;
    }

    public void delete(PlantId id) {
        repository.delete(id);
    }
}
