package org.lucoenergia.conluz.infrastructure.admin.user.disable;

import org.lucoenergia.conluz.domain.admin.user.disable.DisableUserRepository;
import org.lucoenergia.conluz.domain.admin.user.disable.DisableUserService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
public class DisableUserServiceImpl implements DisableUserService {

    private final DisableUserRepository repository;

    public DisableUserServiceImpl(DisableUserRepository repository) {
        this.repository = repository;
    }

    public void disable(UserId id) {
        repository.disable(id);
    }
}
