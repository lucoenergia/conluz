package org.lucoenergia.conluz.infrastructure.production.plant.create;

import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantAlreadyExistsException;
import org.lucoenergia.conluz.domain.production.plant.PlantCannotBeCreatedException;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.shared.PlantCode;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntityMapper;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional
@Repository
public class CreatePlantRepositoryDatabase implements CreatePlantRepository {

    private final UserRepository userRepository;
    private final PlantRepository plantRepository;
    private final UserEntityMapper userEntityMapper;
    private final PlantEntityMapper plantEntityMapper;

    public CreatePlantRepositoryDatabase(UserRepository userRepository, PlantRepository plantRepository, UserEntityMapper userEntityMapper, PlantEntityMapper plantEntityMapper) {
        this.userRepository = userRepository;
        this.plantRepository = plantRepository;
        this.userEntityMapper = userEntityMapper;
        this.plantEntityMapper = plantEntityMapper;
    }

    @Override
    public Plant create(Plant plant, UserId id) {
        Optional<UserEntity> result = userRepository.findById(id.getId());
        if (result.isEmpty()) {
            throw new UserNotFoundException(id);
        }
        if (plantRepository.countByCode(plant.getCode()) > 0) {
            throw new PlantAlreadyExistsException(PlantCode.of(plant.getCode()));
        }

        UserEntity userEntity = result.get();
        PlantEntity plantEntity = new PlantEntity.Builder()
                .setId(UUID.randomUUID())
                .setCode(plant.getCode())
                .setName(plant.getName())
                .setAddress(plant.getAddress())
                .setDescription(plant.getDescription())
                .setInverterProvider(plant.getInverterProvider())
                .setTotalPower(plant.getTotalPower())
                .setConnectionDate(plant.getConnectionDate())
                .build();

        userEntity.addPlant(plantEntity);

        userRepository.save(userEntity);

        Optional<PlantEntity> newPlantEntity = plantRepository.findByCode(plant.getCode());
        if (newPlantEntity.isEmpty()) {
            throw new PlantCannotBeCreatedException();
        }

        Plant newPlant = plantEntityMapper.map(newPlantEntity.get());
        newPlant.setUser(userEntityMapper.map(userEntity));

        return newPlant;
    }
}
