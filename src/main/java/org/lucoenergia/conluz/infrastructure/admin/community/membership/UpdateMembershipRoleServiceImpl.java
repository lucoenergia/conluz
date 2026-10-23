package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.MembershipLastActiveAdminException;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.MembershipWithoutContactEmailException;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.lock.LockCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.LastActiveCommunityAdminRule;
import org.lucoenergia.conluz.domain.admin.community.membership.MembershipInRoster;
import org.lucoenergia.conluz.domain.admin.community.membership.UpdateMembershipRoleRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.UpdateMembershipRoleService;
import org.lucoenergia.conluz.domain.admin.community.membership.lock.LockMembershipRepository;
import org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository;
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
    private final LockCommunityRepository lockCommunityRepository;
    private final LockUserRepository lockUserRepository;
    private final LockMembershipRepository lockMembershipRepository;

    public UpdateMembershipRoleServiceImpl(UpdateMembershipRoleRepository updateMembershipRoleRepository,
                                           GetCommunityRepository getCommunityRepository,
                                           GetOneTimeTokenService getOneTimeTokenService,
                                           GetMembershipsRepository getMembershipsRepository,
                                           LockCommunityRepository lockCommunityRepository,
                                           LockUserRepository lockUserRepository,
                                           LockMembershipRepository lockMembershipRepository) {
        this.updateMembershipRoleRepository = updateMembershipRoleRepository;
        this.getCommunityRepository = getCommunityRepository;
        this.getOneTimeTokenService = getOneTimeTokenService;
        this.getMembershipsRepository = getMembershipsRepository;
        this.lockCommunityRepository = lockCommunityRepository;
        this.lockUserRepository = lockUserRepository;
        this.lockMembershipRepository = lockMembershipRepository;
    }

    /**
     * Lock order: community row, then user row, then membership row. Every demotion and membership deletion in the
     * community locks the community row first, so two of them run one after another and the second counts the active
     * admins the first one left (MEM-033).
     */
    @Override
    public MembershipInRoster updateRole(UUID communityId, UUID userId, CommunityRole role) {
        getCommunityRepository.findById(communityId)
                .orElseThrow(() -> new CommunityNotFoundException(communityId));
        UserId user = UserId.of(userId);
        if (!lockCommunityRepository.lock(communityId) || !lockUserRepository.lock(user)
                || !lockMembershipRepository.lock(user, communityId)) {
            throw new MembershipNotFoundException(communityId, userId);
        }
        CommunityMembership current = getMembershipsRepository.findByUserIdAndCommunityId(userId, communityId)
                .orElseThrow(() -> new MembershipNotFoundException(communityId, userId));
        if (role == CommunityRole.COMMUNITY_ADMIN) {
            requireContactEmailToBecomeAdmin(communityId, userId, current);
        } else if (LastActiveCommunityAdminRule.blocksRemoval(current,
                () -> getMembershipsRepository.countActiveAdmins(communityId))) {
            throw new MembershipLastActiveAdminException(communityId, userId);
        }

        CommunityMembership membership = updateMembershipRoleRepository.updateRole(communityId, userId, role);
        return new MembershipInRoster(membership,
                getOneTimeTokenService
                        .findValidExpiryForMembership(user, communityId, OneTimeTokenPurpose.INVITATION)
                        .orElse(null),
                LastActiveCommunityAdminRule.blocksRemoval(membership,
                        () -> getMembershipsRepository.countActiveAdmins(communityId)));
    }

    /**
     * A community admin is identified and contacted through the contact email of their member record (ADR-0003 §8),
     * so a membership is given the admin role only if it has one. A membership that already has the role is left as
     * it is.
     */
    private void requireContactEmailToBecomeAdmin(UUID communityId, UUID userId, CommunityMembership current) {
        if (current.getRole() != CommunityRole.COMMUNITY_ADMIN && current.getContactEmail() == null) {
            throw new MembershipWithoutContactEmailException(communityId, userId);
        }
    }
}
