package org.lucoenergia.conluz.infrastructure.production.plant.update;


import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.update.UpdatePlantRepository;
import org.lucoenergia.conluz.domain.production.plant.update.UpdatePlantService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
public class UpdatePlantServiceImpl implements UpdatePlantService {

    private final UpdatePlantRepository repository;

    public UpdatePlantServiceImpl(UpdatePlantRepository repository) {
        this.repository = repository;
    }

    public Plant update(Plant plant) {
        return repository.update(plant);
    }
}
