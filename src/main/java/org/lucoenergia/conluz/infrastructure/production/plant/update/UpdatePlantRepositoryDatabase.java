package org.lucoenergia.conluz.infrastructure.production.plant.update;

import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.update.UpdatePlantRepository;
import org.lucoenergia.conluz.domain.shared.PlantId;
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
    private final UserRepository userRepository;
    private final PlantEntityMapper mapper;

    public UpdatePlantRepositoryDatabase(PlantRepository repository, UserRepository userRepository,
                                         PlantEntityMapper mapper) {
        this.repository = repository;
        this.userRepository = userRepository;
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
        if (!currentPlant.getUser().getPersonalId().equals(plant.getUser().getPersonalId())) {
            Optional<UserEntity> userEntityOptional = userRepository.findByPersonalId(plant.getUser().getPersonalId());
            if (userEntityOptional.isEmpty()) {
                throw new UserNotFoundException(plant.getUser().getPersonalId());
            }
            currentPlant.setUser(userEntityOptional.get());
        }

        return mapper.map(repository.save(currentPlant));
    }
}
