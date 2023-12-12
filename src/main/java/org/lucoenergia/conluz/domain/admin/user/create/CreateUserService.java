package org.lucoenergia.conluz.domain.admin.user.create;


import org.lucoenergia.conluz.domain.admin.user.Role;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.user.DefaultAdminUserConfiguration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        User user = new User();

        // Get values from env vars
        user.setPersonalId(defaultAdminUserConfiguration.getDefaultAdminUserId());
        user.setNumber(defaultAdminUserConfiguration.getDefaultAdminUserNumber());
        user.setFullName(defaultAdminUserConfiguration.getDefaultAdminUserFullName());
        user.setAddress(defaultAdminUserConfiguration.getDefaultAdminUserAddress());
        user.setEmail(defaultAdminUserConfiguration.getDefaultAdminUserEmail());
        user.setPassword(defaultAdminUserConfiguration.getDefaultAdminUserPassword());

        // Set role to ADMIN
        user.setRole(Role.ADMIN);

        create(user, user.getPassword());
    }
}
