package org.lucoenergia.conluz.infrastructure.admin.user.disable;

import org.lucoenergia.conluz.domain.admin.user.disable.DisableUserRepository;
import org.lucoenergia.conluz.domain.admin.user.disable.DisableUserService;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.LastPlatformAdminException;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.LastPlatformAdminRule;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.ManagePlatformAdminRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Transactional
@Service
public class DisableUserServiceImpl implements DisableUserService {

    private final DisableUserRepository repository;
    private final GetUserRepository getUserRepository;
    private final ManagePlatformAdminRepository platformAdminRepository;
    private final Clock clock;

    public DisableUserServiceImpl(DisableUserRepository repository,
                                  GetUserRepository getUserRepository,
                                  ManagePlatformAdminRepository platformAdminRepository,
                                  Clock clock) {
        this.repository = repository;
        this.getUserRepository = getUserRepository;
        this.platformAdminRepository = platformAdminRepository;
        this.clock = clock;
    }

    public void disable(UserId id) {
        getUserRepository.findById(id)
                .filter(user -> LastPlatformAdminRule.blocksRevocation(user,
                        platformAdminRepository::countPlatformAdmins))
                .ifPresent(user -> {
                    throw new LastPlatformAdminException();
                });
        repository.disable(id, clock.instant());
    }
}
