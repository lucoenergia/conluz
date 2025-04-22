package org.lucoenergia.conluz.domain.admin.user.disable;

import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
public class DisableUserService {

    private final DisableUserRepository repository;

    public DisableUserService(DisableUserRepository repository) {
        this.repository = repository;
    }

    public void disable(UserId id) {
        repository.disable(id);
    }
}
