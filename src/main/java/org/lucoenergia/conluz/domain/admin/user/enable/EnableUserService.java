package org.lucoenergia.conluz.domain.admin.user.enable;

import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Service;

@Service
public class EnableUserService {

    private final EnableUserRepository repository;

    public EnableUserService(EnableUserRepository repository) {
        this.repository = repository;
    }

    public void enable(UserId id) {
        repository.enable(id);
    }
}
