package org.lucoenergia.conluz.domain.admin.user.create;


import jakarta.validation.Validator;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CreateUserService {

    private final CreateUserRepository repository;
    private final Validator validator;

    public CreateUserService(CreateUserRepository repository, Validator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    public User create(User user) {
        user.enable();
        user.initializeUuid();
        return repository.create(user);
    }
}
