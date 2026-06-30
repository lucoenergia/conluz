package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin;

import org.lucoenergia.conluz.domain.admin.user.platformadmin.LastPlatformAdminException;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.ManagePlatformAdminAccess;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.ManagePlatformAdminRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
public class ManagePlatformAdminAccessImpl implements ManagePlatformAdminAccess {

    private final ManagePlatformAdminRepository repository;

    public ManagePlatformAdminAccessImpl(ManagePlatformAdminRepository repository) {
        this.repository = repository;
    }

    @Override
    public void grant(UserId id) {
        repository.grant(id);
    }

    @Override
    public void revoke(UserId id) {
        // Accepted read-then-write TOCTOU: two simultaneous revokes could theoretically leave zero
        // admins. Not guarded with DB locking by design (single cooperative deployment, human-driven).
        if (repository.countPlatformAdmins() <= 1) {
            throw new LastPlatformAdminException();
        }
        repository.revoke(id);
    }
}
