package org.lucoenergia.conluz.infrastructure.production.plant.delete;

import org.lucoenergia.conluz.domain.production.plant.delete.DeletePlantRepository;
import org.lucoenergia.conluz.domain.production.plant.delete.DeletePlantService;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
public class DeletePlantServiceImpl implements DeletePlantService {

    private final DeletePlantRepository repository;

    public DeletePlantServiceImpl(DeletePlantRepository repository) {
        this.repository = repository;
    }

    public void delete(PlantId id) {
        repository.delete(id);
    }
}
