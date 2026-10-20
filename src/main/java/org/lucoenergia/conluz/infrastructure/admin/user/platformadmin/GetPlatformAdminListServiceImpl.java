package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin;

import org.lucoenergia.conluz.domain.admin.user.platformadmin.GetPlatformAdminListService;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.GetPlatformAdminsService;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.PlatformAdminListEntry;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.GetPlatformAdminInvitationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class GetPlatformAdminListServiceImpl implements GetPlatformAdminListService {

    private final GetPlatformAdminsService getPlatformAdminsService;
    private final GetPlatformAdminInvitationRepository getInvitationRepository;
    private final Clock clock;

    public GetPlatformAdminListServiceImpl(GetPlatformAdminsService getPlatformAdminsService,
                                           GetPlatformAdminInvitationRepository getInvitationRepository,
                                           Clock clock) {
        this.getPlatformAdminsService = getPlatformAdminsService;
        this.getInvitationRepository = getInvitationRepository;
        this.clock = clock;
    }

    /**
     * Two queries whatever the number of rows: the platform admins, then the pending invitations with their link's
     * expiry. The pending rows never reach the platform admins' count: it is taken from the first query alone.
     */
    @Override
    public List<PlatformAdminListEntry> findAll() {
        List<PlatformAdminListEntry> entries = new ArrayList<>(getPlatformAdminsService.findAll());
        entries.addAll(getInvitationRepository.findPendingRows(clock.instant()));
        return entries;
    }
}
