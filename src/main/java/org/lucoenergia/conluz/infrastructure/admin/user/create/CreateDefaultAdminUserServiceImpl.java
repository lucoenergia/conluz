package org.lucoenergia.conluz.infrastructure.admin.user.create;

import org.lucoenergia.conluz.domain.admin.config.init.get.GetConfigRepository;
import org.lucoenergia.conluz.domain.admin.config.init.update.UpdateConfigRepository;
import org.lucoenergia.conluz.domain.admin.user.DefaultAdminUser;
import org.lucoenergia.conluz.domain.admin.user.create.CreateDefaultAdminUserService;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserService;
import org.lucoenergia.conluz.domain.admin.user.create.DefaultAdminUserAlreadyInitializedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CreateDefaultAdminUserServiceImpl implements CreateDefaultAdminUserService {

    private final CreateUserService createUserService;
    private final GetConfigRepository getConfigRepository;
    private final UpdateConfigRepository updateConfigRepository;

    public CreateDefaultAdminUserServiceImpl(CreateUserService createUserService, GetConfigRepository getConfigRepository,
                                             UpdateConfigRepository updateConfigRepository) {
        this.createUserService = createUserService;
        this.getConfigRepository = getConfigRepository;
        this.updateConfigRepository = updateConfigRepository;
    }

    public void createDefaultAdminUser(DefaultAdminUser user) {

        if (getConfigRepository.isDefaultAdminInitialized()) {
            throw new DefaultAdminUserAlreadyInitializedException();
        }

        createUserService.create(user);

        updateConfigRepository.markDefaultAdminUserAsInitialized();
    }
}
