package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.UpdateMembershipRoleRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.MembershipWithInvitation;
import org.lucoenergia.conluz.domain.admin.community.membership.UpdateMembershipRoleService;
import org.lucoenergia.conluz.domain.admin.user.token.GetOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class UpdateMembershipRoleServiceImpl implements UpdateMembershipRoleService {

    private final UpdateMembershipRoleRepository updateMembershipRoleRepository;
    private final GetCommunityRepository getCommunityRepository;
    private final GetOneTimeTokenService getOneTimeTokenService;

    public UpdateMembershipRoleServiceImpl(UpdateMembershipRoleRepository updateMembershipRoleRepository,
                                           GetCommunityRepository getCommunityRepository,
                                           GetOneTimeTokenService getOneTimeTokenService) {
        this.updateMembershipRoleRepository = updateMembershipRoleRepository;
        this.getCommunityRepository = getCommunityRepository;
        this.getOneTimeTokenService = getOneTimeTokenService;
    }

    @Override
    public MembershipWithInvitation updateRole(UUID communityId, UUID userId, CommunityRole role) {
        getCommunityRepository.findById(communityId)
                .orElseThrow(() -> new CommunityNotFoundException(communityId));

        CommunityMembership membership = updateMembershipRoleRepository.updateRole(communityId, userId, role);
        return new MembershipWithInvitation(membership, getOneTimeTokenService
                .findValidExpiryForMembership(UserId.of(userId), communityId, OneTimeTokenPurpose.INVITATION)
                .orElse(null));
    }
}
