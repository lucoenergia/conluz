package org.lucoenergia.conluz.infrastructure.admin.user.disable;

import org.lucoenergia.conluz.domain.admin.user.disable.DisableUserRepository;
import org.lucoenergia.conluz.domain.admin.user.disable.DisableUserService;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.LastPlatformAdminException;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.LastPlatformAdminRule;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.LockPlatformAdminsRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.GetPlatformAdminsRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Transactional
@Service
public class DisableUserServiceImpl implements DisableUserService {

    private final DisableUserRepository repository;
    private final GetUserRepository getUserRepository;
    private final GetPlatformAdminsRepository platformAdminRepository;
    private final LockPlatformAdminsRepository lockPlatformAdminsRepository;
    private final Clock clock;

    public DisableUserServiceImpl(DisableUserRepository repository,
                                  GetUserRepository getUserRepository,
                                  GetPlatformAdminsRepository platformAdminRepository,
                                  LockPlatformAdminsRepository lockPlatformAdminsRepository,
                                  Clock clock) {
        this.repository = repository;
        this.getUserRepository = getUserRepository;
        this.platformAdminRepository = platformAdminRepository;
        this.lockPlatformAdminsRepository = lockPlatformAdminsRepository;
        this.clock = clock;
    }

    /**
     * Disables the account, or refuses (ACC-008). The platform-admin set is locked before the target is read, so a
     * disabling, a revoke or a deletion running at the same time cannot both remove the last two enabled platform
     * admins (ACC-013).
     */
    public void disable(UserId id) {
        lockPlatformAdminsRepository.lock();
        getUserRepository.findById(id)
                .filter(user -> LastPlatformAdminRule.blocksRevocation(user,
                        platformAdminRepository::countPlatformAdmins))
                .ifPresent(user -> {
                    throw new LastPlatformAdminException();
                });
        repository.disable(id, clock.instant());
    }
}
