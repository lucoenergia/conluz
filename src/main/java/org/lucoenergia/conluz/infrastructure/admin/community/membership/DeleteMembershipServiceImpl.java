package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.MembershipOwnsSuppliesException;
import org.lucoenergia.conluz.domain.admin.community.membership.DeleteMembershipRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.DeleteMembershipService;
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
    private final LockUserRepository lockUserRepository;
    private final LockMembershipRepository lockMembershipRepository;
    private final GetSupplyRepository getSupplyRepository;

    public DeleteMembershipServiceImpl(DeleteMembershipRepository deleteMembershipRepository,
                                       LockUserRepository lockUserRepository,
                                       LockMembershipRepository lockMembershipRepository,
                                       GetSupplyRepository getSupplyRepository) {
        this.deleteMembershipRepository = deleteMembershipRepository;
        this.lockUserRepository = lockUserRepository;
        this.lockMembershipRepository = lockMembershipRepository;
        this.getSupplyRepository = getSupplyRepository;
    }

    /**
     * Deletes the membership, or refuses and deletes nothing when the user owns supplies in the community (MEM-029).
     * Lock order: user row, then membership row. Supply creation locks the user row before checking the owner's
     * membership, so a supply created concurrently is either seen here, and refuses the deletion, or finds the
     * membership gone.
     */
    @Override
    public void delete(UUID communityId, UUID userId) {
        UserId user = UserId.of(userId);
        if (!lockUserRepository.lock(user) || !lockMembershipRepository.lock(user, communityId)) {
            throw new MembershipNotFoundException(communityId, userId);
        }
        if (getSupplyRepository.existsByOwnerAndCommunity(user, communityId)) {
            throw new MembershipOwnsSuppliesException(communityId, userId);
        }
        deleteMembershipRepository.delete(communityId, userId);
    }
}
