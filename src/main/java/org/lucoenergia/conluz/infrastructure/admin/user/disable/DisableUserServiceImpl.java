package org.lucoenergia.conluz.infrastructure.admin.user.disable;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.disable.DisableUserRepository;
import org.lucoenergia.conluz.domain.admin.user.disable.DisableUserService;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.LastPlatformAdminException;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.ManagePlatformAdminRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
public class DisableUserServiceImpl implements DisableUserService {

    private final DisableUserRepository repository;
    private final GetUserRepository getUserRepository;
    private final ManagePlatformAdminRepository platformAdminRepository;

    public DisableUserServiceImpl(DisableUserRepository repository,
                                  GetUserRepository getUserRepository,
                                  ManagePlatformAdminRepository platformAdminRepository) {
        this.repository = repository;
        this.getUserRepository = getUserRepository;
        this.platformAdminRepository = platformAdminRepository;
    }

    public void disable(UserId id) {
        getUserRepository.findById(id)
                .filter(User::isPlatformAdmin)
                .ifPresent(user -> {
                    if (platformAdminRepository.countPlatformAdmins() <= 1) {
                        throw new LastPlatformAdminException();
                    }
                });
        repository.disable(id);
    }
}
