package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.GetPlatformAdminsRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.LastPlatformAdminException;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.LastPlatformAdminRule;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.ManagePlatformAdminAccessService;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.ManagePlatformAdminRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.UserNotPlatformAdminException;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
public class ManagePlatformAdminAccessServiceImpl implements ManagePlatformAdminAccessService {

    private final ManagePlatformAdminRepository repository;
    private final GetPlatformAdminsRepository getPlatformAdminsRepository;
    private final GetUserRepository getUserRepository;

    public ManagePlatformAdminAccessServiceImpl(ManagePlatformAdminRepository repository,
                                                GetPlatformAdminsRepository getPlatformAdminsRepository,
                                                GetUserRepository getUserRepository) {
        this.repository = repository;
        this.getPlatformAdminsRepository = getPlatformAdminsRepository;
        this.getUserRepository = getUserRepository;
    }

    @Override
    public void revoke(UserId id) {
        // Accepted read-then-write TOCTOU: two simultaneous revokes could theoretically leave zero
        // admins. Not guarded with DB locking by design (single cooperative deployment, human-driven).
        User target = getUserRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        if (!target.isPlatformAdmin()) {
            throw new UserNotPlatformAdminException(id);
        }
        if (LastPlatformAdminRule.blocksRevocation(target, getPlatformAdminsRepository.countPlatformAdmins())) {
            throw new LastPlatformAdminException();
        }
        repository.revoke(id);
    }
}
