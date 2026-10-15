package org.lucoenergia.conluz.infrastructure.admin.community.admins;

import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.admins.EnableCommunityAdminService;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
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

    public EnableCommunityAdminServiceImpl(GetCommunityRepository getCommunityRepository,
                                          LockUserRepository lockUserRepository,
                                          LockMembershipRepository lockMembershipRepository,
                                          SetMembershipEnabledRepository setMembershipEnabledRepository) {
        this.getCommunityRepository = getCommunityRepository;
        this.lockUserRepository = lockUserRepository;
        this.lockMembershipRepository = lockMembershipRepository;
        this.setMembershipEnabledRepository = setMembershipEnabledRepository;
    }

    @Override
    public void enable(UUID communityId, UserId userId) {
        getCommunityRepository.findById(communityId)
                .orElseThrow(() -> new CommunityNotFoundException(communityId));

        // Lock order: user row, then membership row, as sending an invitation takes them, so a send never sees the
        // state half-way through a change
        if (!lockUserRepository.lock(userId) || !lockMembershipRepository.lock(userId, communityId)
                || !setMembershipEnabledRepository.setEnabled(communityId, userId, CommunityRole.COMMUNITY_ADMIN,
                true)) {
            throw new MembershipNotFoundException(communityId, userId.getId());
        }

        LOGGER.info("Community admin enabled: userId={}, communityId={}", userId.getId(), communityId);
    }
}
