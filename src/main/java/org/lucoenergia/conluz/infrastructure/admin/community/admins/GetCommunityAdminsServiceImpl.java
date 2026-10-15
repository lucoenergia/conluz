package org.lucoenergia.conluz.infrastructure.admin.community.admins;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.admins.CommunityAdmin;
import org.lucoenergia.conluz.domain.admin.community.admins.GetCommunityAdminsService;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.user.token.GetOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GetCommunityAdminsServiceImpl implements GetCommunityAdminsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetCommunityAdminsServiceImpl.class);

    private final GetMembershipsRepository getMembershipsRepository;
    private final GetOneTimeTokenService getOneTimeTokenService;

    public GetCommunityAdminsServiceImpl(GetMembershipsRepository getMembershipsRepository,
                                         GetOneTimeTokenService getOneTimeTokenService) {
        this.getMembershipsRepository = getMembershipsRepository;
        this.getOneTimeTokenService = getOneTimeTokenService;
    }

    @Override
    public List<CommunityAdmin> findByCommunityId(UUID communityId) {
        // Only admin memberships are read: nothing of the community's members, nor of the admins' memberships of
        // other communities, is loaded
        List<CommunityMembership> admins = getMembershipsRepository.findByCommunityIdAndRole(communityId,
                CommunityRole.COMMUNITY_ADMIN);
        // One query for the whole list
        Map<UUID, Instant> invitationExpiries = getOneTimeTokenService.findValidExpiriesInCommunity(communityId,
                OneTimeTokenPurpose.INVITATION);

        LOGGER.info("Community admins listed: communityId={}, count={}", communityId, admins.size());
        return admins.stream()
                .map(admin -> new CommunityAdmin(admin.getUser().getPersonalId(), admin,
                        invitationExpiries.get(admin.getId())))
                .toList();
    }
}
