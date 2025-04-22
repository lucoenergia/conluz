package org.lucoenergia.conluz.infrastructure.admin.user.enable;

import org.lucoenergia.conluz.domain.admin.user.enable.EnableUserRepository;
import org.lucoenergia.conluz.domain.admin.user.enable.EnableUserService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
public class EnableUserServiceImpl implements EnableUserService {

    private final EnableUserRepository repository;

    public EnableUserServiceImpl(EnableUserRepository repository) {
        this.repository = repository;
    }

    public void enable(UserId id) {
        repository.enable(id);
    }
}
