package org.lucoenergia.conluz.infrastructure.admin.config.init;

import org.lucoenergia.conluz.domain.admin.config.init.InitService;
import org.lucoenergia.conluz.domain.admin.user.DefaultAdminUser;
import org.lucoenergia.conluz.domain.admin.user.create.CreateDefaultAdminUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InitServiceImpl implements InitService {

    private final CreateDefaultAdminUserService createDefaultAdminUserService;

    public InitServiceImpl(CreateDefaultAdminUserService createDefaultAdminUserService) {
        this.createDefaultAdminUserService = createDefaultAdminUserService;
    }

    public void init(DefaultAdminUser user) {
        createDefaultAdminUserService.createDefaultAdminUser(user);
    }
}
