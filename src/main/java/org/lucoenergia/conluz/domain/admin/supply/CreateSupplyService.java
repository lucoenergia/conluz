package org.lucoenergia.conluz.domain.admin.supply;


import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CreateSupplyService {

    private final CreateSupplyRepository repository;
    private final GetUserRepository getUserRepository;

    public CreateSupplyService(CreateSupplyRepository repository, GetUserRepository getUserRepository) {
        this.repository = repository;
        this.getUserRepository = getUserRepository;
    }

    public Supply create(Supply supply, UserId id) {
        supply.enable();
        supply.initializeUuid();
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
