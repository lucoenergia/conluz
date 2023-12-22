package org.lucoenergia.conluz.domain.admin.config.init;

import org.lucoenergia.conluz.domain.admin.user.DefaultAdminUser;
import org.lucoenergia.conluz.domain.admin.user.create.CreateDefaultAdminUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InitService {

    private final CreateDefaultAdminUserService createDefaultAdminUserService;

    public InitService(CreateDefaultAdminUserService createDefaultAdminUserService) {
        this.createDefaultAdminUserService = createDefaultAdminUserService;
    }

    public void init(DefaultAdminUser user) {
        createDefaultAdminUserService.createDefaultAdminUser(user);
    }
}
