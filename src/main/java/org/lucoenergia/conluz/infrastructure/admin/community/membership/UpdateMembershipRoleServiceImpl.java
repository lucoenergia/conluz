package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.MembershipWithoutContactEmailException;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
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
    private final GetMembershipsRepository getMembershipsRepository;

    public UpdateMembershipRoleServiceImpl(UpdateMembershipRoleRepository updateMembershipRoleRepository,
                                           GetCommunityRepository getCommunityRepository,
                                           GetOneTimeTokenService getOneTimeTokenService,
                                           GetMembershipsRepository getMembershipsRepository) {
        this.updateMembershipRoleRepository = updateMembershipRoleRepository;
        this.getCommunityRepository = getCommunityRepository;
        this.getOneTimeTokenService = getOneTimeTokenService;
        this.getMembershipsRepository = getMembershipsRepository;
    }

    @Override
    public MembershipWithInvitation updateRole(UUID communityId, UUID userId, CommunityRole role) {
        getCommunityRepository.findById(communityId)
                .orElseThrow(() -> new CommunityNotFoundException(communityId));
        if (role == CommunityRole.COMMUNITY_ADMIN) {
            requireContactEmailToBecomeAdmin(communityId, userId);
        }

        CommunityMembership membership = updateMembershipRoleRepository.updateRole(communityId, userId, role);
        return new MembershipWithInvitation(membership, getOneTimeTokenService
                .findValidExpiryForMembership(UserId.of(userId), communityId, OneTimeTokenPurpose.INVITATION)
                .orElse(null));
    }

    /**
     * A community admin is identified and contacted through the contact email of their member record (ADR-0003 §8),
     * so a membership is given the admin role only if it has one. A membership that already has the role is left as
     * it is. A missing membership is reported by the update itself.
     */
    private void requireContactEmailToBecomeAdmin(UUID communityId, UUID userId) {
        getMembershipsRepository.findByUserIdAndCommunityId(userId, communityId)
                .filter(current -> current.getRole() != CommunityRole.COMMUNITY_ADMIN)
                .filter(current -> current.getContactEmail() == null)
                .ifPresent(current -> {
                    throw new MembershipWithoutContactEmailException(communityId, userId);
                });
    }
}
