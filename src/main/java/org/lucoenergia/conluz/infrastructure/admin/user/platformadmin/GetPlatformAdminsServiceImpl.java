package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.GetPlatformAdminsRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.GetPlatformAdminsService;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.LastPlatformAdminRule;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.PlatformAdmin;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class GetPlatformAdminsServiceImpl implements GetPlatformAdminsService {

    private final GetPlatformAdminsRepository repository;

    public GetPlatformAdminsServiceImpl(GetPlatformAdminsRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<PlatformAdmin> findAll() {
        List<User> admins = repository.findAll();
        // The list holds every platform admin, so its enabled ones are exactly what the revoke counts: no second
        // query, and no window between the list and the count
        long enabled = admins.stream().filter(User::isEnabled).count();
        boolean revocable = !LastPlatformAdminRule.blocksRevocation(enabled);
        return admins.stream()
                .map(admin -> new PlatformAdmin(admin, revocable))
                .toList();
    }
}
