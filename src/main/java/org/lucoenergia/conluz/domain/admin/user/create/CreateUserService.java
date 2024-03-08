package org.lucoenergia.conluz.domain.admin.user.create;


import org.lucoenergia.conluz.domain.admin.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CreateUserService {

    private final CreateUserRepository repository;

    public CreateUserService(CreateUserRepository repository) {
        this.repository = repository;
    }

    public User create(User user) {
        user.enable();
        user.initializeUuid();
        return repository.create(user);
    }
}
