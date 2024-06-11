package org.lucoenergia.conluz.domain.production.plant.create;


import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CreatePlantService {

    private final CreatePlantRepository repository;
    private final GetSupplyRepository getSupplyRepository;

    public CreatePlantService(CreatePlantRepository repository, GetSupplyRepository getSupplyRepository) {
        this.repository = repository;
        this.getSupplyRepository = getSupplyRepository;
    }

    public Plant create(Plant plant, SupplyId id) {
        plant.initializeUuid();
        return repository.create(plant, id);
    }

    public Plant create(Plant plant, SupplyCode code) {
        Optional<Supply> supply = getSupplyRepository.findByCode(code);
        if (supply.isEmpty()) {
            throw new SupplyNotFoundException(code);
        }
        return create(plant, SupplyId.of(supply.get().getId()));
    }
}
