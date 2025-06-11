package org.lucoenergia.conluz.infrastructure.admin.user.update;


import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.update.UpdateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.update.UpdateUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
public class UpdateUserServiceImpl implements UpdateUserService {

    private final UpdateUserRepository repository;

    public UpdateUserServiceImpl(UpdateUserRepository repository) {
        this.repository = repository;
    }

    public User update(User user) {
        return repository.update(user);
    }
}
