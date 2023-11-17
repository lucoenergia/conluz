package org.lucoenergia.conluz.domain.admin.user;


import org.springframework.stereotype.Service;

@Service
public class CreateUserService {

    private final CreateUserRepository repository;

    public CreateUserService(CreateUserRepository repository) {
        this.repository = repository;
    }

    public User create(User user, String password) {
        return repository.create(user, password);
    }
}
