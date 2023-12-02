package org.lucoenergia.conluz.infrastructure.shared.startup;

import org.lucoenergia.conluz.domain.admin.user.create.CreateUserService;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order
public class InitAdminUserApplicationRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final CreateUserService createUserService;

    public InitAdminUserApplicationRunner(UserRepository userRepository, CreateUserService createUserService) {
        this.userRepository = userRepository;
        this.createUserService = createUserService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (isAnyUserPresent()) {
            return;
        }
        createDefaultUser();
    }

    private boolean isAnyUserPresent() {
        return userRepository.count() > 0;
    }

    private void createDefaultUser() {
        createUserService.createDefaultAdminUser();
    }
}
