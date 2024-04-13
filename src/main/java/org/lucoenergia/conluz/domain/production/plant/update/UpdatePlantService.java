package org.lucoenergia.conluz.domain.production.plant.update;


import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.springframework.stereotype.Service;

@Service
public class UpdatePlantService {

    private final UpdatePlantRepository repository;

    public UpdatePlantService(UpdatePlantRepository repository) {
        this.repository = repository;
    }

    public Plant update(Plant plant) {
        return repository.update(plant);
    }
}
