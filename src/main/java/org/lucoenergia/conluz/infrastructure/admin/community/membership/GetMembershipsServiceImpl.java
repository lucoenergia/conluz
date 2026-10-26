package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsService;
import org.lucoenergia.conluz.domain.admin.community.membership.LastActiveCommunityAdminRule;
import org.lucoenergia.conluz.domain.admin.community.membership.MembershipInRoster;
import org.lucoenergia.conluz.domain.admin.user.token.GetOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GetMembershipsServiceImpl implements GetMembershipsService {

    private final GetMembershipsRepository getMembershipsRepository;
    private final GetOneTimeTokenService getOneTimeTokenService;

    public GetMembershipsServiceImpl(GetMembershipsRepository getMembershipsRepository,
                                     GetOneTimeTokenService getOneTimeTokenService) {
        this.getMembershipsRepository = getMembershipsRepository;
        this.getOneTimeTokenService = getOneTimeTokenService;
    }

    @Override
    public List<MembershipInRoster> findByCommunityId(UUID communityId) {
        // The repository answers CommunityNotFoundException for a community that does not exist
        List<CommunityMembership> memberships = getMembershipsRepository.findByCommunityId(communityId);
        Map<UUID, Instant> invitationExpiries = getOneTimeTokenService.findValidExpiriesInCommunity(communityId,
                OneTimeTokenPurpose.INVITATION);
        long activeAdmins = LastActiveCommunityAdminRule.activeAdmins(memberships);
        return memberships.stream()
                .map(membership -> new MembershipInRoster(membership,
                        invitationExpiries.get(membership.getId()),
                        LastActiveCommunityAdminRule.blocksRemoval(membership, () -> activeAdmins)))
                .toList();
    }
}
