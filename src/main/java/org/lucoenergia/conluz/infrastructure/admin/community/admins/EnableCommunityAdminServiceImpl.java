package org.lucoenergia.conluz.infrastructure.admin.community.admins;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.admins.EnableCommunityAdminService;
import org.lucoenergia.conluz.domain.admin.community.admins.notification.AdminChangeActor;
import org.lucoenergia.conluz.domain.admin.community.admins.notification.CommunityAdminChange;
import org.lucoenergia.conluz.domain.admin.community.admins.notification.CommunityAdminChangeNotifier;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.SetMembershipEnabledRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.lock.LockMembershipRepository;
import org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class EnableCommunityAdminServiceImpl implements EnableCommunityAdminService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EnableCommunityAdminServiceImpl.class);

    private final GetCommunityRepository getCommunityRepository;
    private final LockUserRepository lockUserRepository;
    private final LockMembershipRepository lockMembershipRepository;
    private final SetMembershipEnabledRepository setMembershipEnabledRepository;
    private final GetMembershipsRepository getMembershipsRepository;
    private final CommunityAdminChangeNotifier notifier;

    public EnableCommunityAdminServiceImpl(GetCommunityRepository getCommunityRepository,
                                          LockUserRepository lockUserRepository,
                                          LockMembershipRepository lockMembershipRepository,
                                          SetMembershipEnabledRepository setMembershipEnabledRepository,
                                          GetMembershipsRepository getMembershipsRepository,
                                          CommunityAdminChangeNotifier notifier) {
        this.getCommunityRepository = getCommunityRepository;
        this.lockUserRepository = lockUserRepository;
        this.lockMembershipRepository = lockMembershipRepository;
        this.setMembershipEnabledRepository = setMembershipEnabledRepository;
        this.getMembershipsRepository = getMembershipsRepository;
        this.notifier = notifier;
    }

    @Override
    public void enable(UUID communityId, UserId userId, UserId enabledBy) {
        getCommunityRepository.findById(communityId)
                .orElseThrow(() -> new CommunityNotFoundException(communityId));

        // Lock order: user row, then membership row, as sending an invitation takes them, so a send never sees the
        // state half-way through a change
        if (!lockUserRepository.lock(userId) || !lockMembershipRepository.lock(userId, communityId)) {
            throw new MembershipNotFoundException(communityId, userId.getId());
        }
        // Read under the locks, before the write, so the notification compares the state the change found
        CommunityMembership before = getMembershipsRepository.findByUserIdAndCommunityId(userId.getId(), communityId)
                .orElseThrow(() -> new MembershipNotFoundException(communityId, userId.getId()));
        if (!setMembershipEnabledRepository.setEnabled(communityId, userId, CommunityRole.COMMUNITY_ADMIN, true)) {
            throw new MembershipNotFoundException(communityId, userId.getId());
        }
        CommunityMembership after = getMembershipsRepository.findByUserIdAndCommunityId(userId.getId(), communityId)
                .orElseThrow(() -> new MembershipNotFoundException(communityId, userId.getId()));
        notifier.notify(CommunityAdminChange.REACTIVATED, before, after, AdminChangeActor.platformOperator(enabledBy));

        LOGGER.info("Community admin enabled: userId={}, communityId={}", userId.getId(), communityId);
    }
}
