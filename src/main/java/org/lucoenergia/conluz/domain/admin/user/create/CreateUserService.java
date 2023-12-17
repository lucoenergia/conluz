package org.lucoenergia.conluz.domain.admin.user.create;


import org.lucoenergia.conluz.domain.admin.user.Role;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.user.DefaultAdminUserConfiguration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
public class CreateUserService {

    private final CreateUserRepository repository;
    private final DefaultAdminUserConfiguration defaultAdminUserConfiguration;

    public CreateUserService(CreateUserRepository repository, DefaultAdminUserConfiguration defaultAdminUserConfiguration) {
        this.repository = repository;
        this.defaultAdminUserConfiguration = defaultAdminUserConfiguration;
    }

    public User create(User user, String password) {
        user.enable();
        user.initializeUuid();
        return repository.create(user, password);
    }

    public void createDefaultAdminUser() {

        Optional<User> userOptional = defaultAdminUserConfiguration.getDefaultAdminUser();

        userOptional.ifPresent(user -> {
            // Set role to ADMIN
            user.setRole(Role.ADMIN);

            create(user, user.getPassword());
        });
    }
}
