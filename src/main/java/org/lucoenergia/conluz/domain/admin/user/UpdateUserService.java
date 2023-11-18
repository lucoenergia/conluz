package org.lucoenergia.conluz.domain.admin.user;


import org.springframework.stereotype.Service;

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
