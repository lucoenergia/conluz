package org.lucoenergia.conluz.domain.admin.supply;


import org.lucoenergia.conluz.domain.admin.user.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Service;

@Service
public class CreateSupplyService {

    private final CreateSupplyRepository repository;

    public CreateSupplyService(CreateSupplyRepository repository) {
        this.repository = repository;
    }

    public Supply create(Supply supply, UserId id) {
        return repository.create(supply, id);
    }
}
