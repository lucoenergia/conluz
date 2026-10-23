package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.MembershipLastActiveAdminException;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.MembershipOwnsSuppliesException;
import org.lucoenergia.conluz.domain.admin.community.lock.LockCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.DeleteMembershipRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.DeleteMembershipService;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.LastActiveCommunityAdminRule;
import org.lucoenergia.conluz.domain.admin.community.membership.lock.LockMembershipRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class DeleteMembershipServiceImpl implements DeleteMembershipService {

    private final DeleteMembershipRepository deleteMembershipRepository;
    private final LockCommunityRepository lockCommunityRepository;
    private final LockUserRepository lockUserRepository;
    private final LockMembershipRepository lockMembershipRepository;
    private final GetMembershipsRepository getMembershipsRepository;
    private final GetSupplyRepository getSupplyRepository;

    public DeleteMembershipServiceImpl(DeleteMembershipRepository deleteMembershipRepository,
                                       LockCommunityRepository lockCommunityRepository,
                                       LockUserRepository lockUserRepository,
                                       LockMembershipRepository lockMembershipRepository,
                                       GetMembershipsRepository getMembershipsRepository,
                                       GetSupplyRepository getSupplyRepository) {
        this.deleteMembershipRepository = deleteMembershipRepository;
        this.lockCommunityRepository = lockCommunityRepository;
        this.lockUserRepository = lockUserRepository;
        this.lockMembershipRepository = lockMembershipRepository;
        this.getMembershipsRepository = getMembershipsRepository;
        this.getSupplyRepository = getSupplyRepository;
    }

    /**
     * Deletes the membership, or refuses and deletes nothing when it is the community's last active admin (MEM-033)
     * or when the user owns supplies in the community (MEM-029), in that order: deleting the supplies would not make
     * the deletion of the last active admin possible.
     * Lock order: community row, then user row, then membership row. Every demotion and membership deletion in the
     * community locks the community row first, so the second of two counts the active admins the first one left.
     * Supply creation locks the user row before checking the owner's membership, so a supply created concurrently is
     * either seen here, and refuses the deletion, or finds the membership gone.
     */
    @Override
    public void delete(UUID communityId, UUID userId) {
        UserId user = UserId.of(userId);
        if (!lockCommunityRepository.lock(communityId) || !lockUserRepository.lock(user)
                || !lockMembershipRepository.lock(user, communityId)) {
            throw new MembershipNotFoundException(communityId, userId);
        }
        CommunityMembership membership = getMembershipsRepository.findByUserIdAndCommunityId(userId, communityId)
                .orElseThrow(() -> new MembershipNotFoundException(communityId, userId));
        if (LastActiveCommunityAdminRule.blocksRemoval(membership,
                () -> getMembershipsRepository.countActiveAdmins(communityId))) {
            throw new MembershipLastActiveAdminException(communityId, userId);
        }
        if (getSupplyRepository.existsByOwnerAndCommunity(user, communityId)) {
            throw new MembershipOwnsSuppliesException(communityId, userId);
        }
        deleteMembershipRepository.delete(communityId, userId);
    }
}
