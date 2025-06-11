package org.lucoenergia.conluz.infrastructure.admin.user.create;


import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CreateUserServiceImpl implements CreateUserService {

    private final CreateUserRepository repository;

    public CreateUserServiceImpl(CreateUserRepository repository) {
        this.repository = repository;
    }

    public User create(User user) {
        user.enable();
        user.initializeUuid();
        return repository.create(user);
    }
}
