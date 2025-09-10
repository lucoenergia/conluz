package org.lucoenergia.conluz.infrastructure.admin.supply.create;


import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Transactional
@Service
public class CreateSupplyServiceImpl implements CreateSupplyService {

    private final CreateSupplyRepository repository;
    private final GetUserRepository getUserRepository;

    public CreateSupplyServiceImpl(CreateSupplyRepository repository, GetUserRepository getUserRepository) {
        this.repository = repository;
        this.getUserRepository = getUserRepository;
    }

    @Override
    public Supply create(Supply supply, UserId id) {
        supply.enable();
        supply.initializeUuid();

        // If name is not provided, use the ID as the default name
        if (supply.getName() == null) {
            supply = new Supply.Builder()
                    .withId(supply.getId())
                    .withCode(supply.getCode())
                    .withUser(supply.getUser())
                    .withName(supply.getAddress())
                    .withAddress(supply.getAddress())
                    .withAddressRef(supply.getAddressRef())
                    .withPartitionCoefficient(supply.getPartitionCoefficient())
                    .withEnabled(supply.getEnabled())
                    .withValidDateFrom(supply.getValidDateFrom())
                    .withDistributor(supply.getDistributor())
                    .withDistributorCode(supply.getDistributorCode())
                    .withPointType(supply.getPointType())
                    .withThirdParty(supply.isThirdParty())
                    .withShellyMac(supply.getShellyMac())
                    .withShellyId(supply.getShellyId())
                    .withShellyMqttPrefix(supply.getShellyMqttPrefix())
                    .build();
        }

        return repository.create(supply, id);
    }

    public Supply create(Supply supply, UserPersonalId id) {
        Optional<User> user = getUserRepository.findByPersonalId(id);
        if (user.isEmpty()) {
            throw new UserNotFoundException(id);
        }
        return create(supply, UserId.of(user.get().getId()));
    }
}
