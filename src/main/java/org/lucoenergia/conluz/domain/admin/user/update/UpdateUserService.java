package org.lucoenergia.conluz.domain.admin.user.update;


import org.lucoenergia.conluz.domain.admin.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
public class UpdateUserService {

    private final UpdateUserRepository repository;

    public UpdateUserService(UpdateUserRepository repository) {
        this.repository = repository;
    }

    public User update(User user) {
        return repository.update(user);
    }
}
