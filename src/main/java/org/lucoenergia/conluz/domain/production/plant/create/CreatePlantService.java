package org.lucoenergia.conluz.domain.production.plant.create;


import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CreatePlantService {

    private final CreatePlantRepository repository;
    private final GetUserRepository getUserRepository;

    public CreatePlantService(CreatePlantRepository repository, GetUserRepository getUserRepository) {
        this.repository = repository;
        this.getUserRepository = getUserRepository;
    }

    public Plant create(Plant plant, UserId id) {
        plant.initializeUuid();
        return repository.create(plant, id);
    }

    public Plant create(Plant supply, UserPersonalId id) {
        Optional<User> user = getUserRepository.findByPersonalId(id);
        if (user.isEmpty()) {
            throw new UserNotFoundException(id);
        }
        return create(supply, UserId.of(user.get().getId()));
    }
}
