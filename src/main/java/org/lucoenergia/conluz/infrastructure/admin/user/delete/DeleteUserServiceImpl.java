package org.lucoenergia.conluz.infrastructure.admin.user.delete;

import org.lucoenergia.conluz.domain.admin.community.membership.DeleteMembershipRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.delete.DeleteUserRepository;
import org.lucoenergia.conluz.domain.admin.user.delete.DeleteUserService;
import org.lucoenergia.conluz.domain.admin.user.delete.UserOwnsSuppliesException;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.LastPlatformAdminException;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.LastPlatformAdminRule;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.LockPlatformAdminsRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.GetPlatformAdminsRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
public class DeleteUserServiceImpl implements DeleteUserService {

    private final DeleteUserRepository repository;
    private final GetUserRepository getUserRepository;
    private final GetPlatformAdminsRepository platformAdminRepository;
    private final LockUserRepository lockUserRepository;
    private final GetSupplyRepository getSupplyRepository;
    private final DeleteMembershipRepository deleteMembershipRepository;
    private final LockPlatformAdminsRepository lockPlatformAdminsRepository;

    public DeleteUserServiceImpl(DeleteUserRepository repository,
                                 GetUserRepository getUserRepository,
                                 GetPlatformAdminsRepository platformAdminRepository,
                                 LockUserRepository lockUserRepository,
                                 GetSupplyRepository getSupplyRepository,
                                 DeleteMembershipRepository deleteMembershipRepository,
                                 LockPlatformAdminsRepository lockPlatformAdminsRepository) {
        this.repository = repository;
        this.getUserRepository = getUserRepository;
        this.platformAdminRepository = platformAdminRepository;
        this.lockUserRepository = lockUserRepository;
        this.getSupplyRepository = getSupplyRepository;
        this.deleteMembershipRepository = deleteMembershipRepository;
        this.lockPlatformAdminsRepository = lockPlatformAdminsRepository;
    }

    /**
     * Deletes the account with its memberships, their member records and every token, or refuses and deletes nothing
     * (ACC-009). The platform-admin set is locked first, so a deletion, a revoke or a disabling running at the same
     * time cannot both remove the last two enabled platform admins (ACC-013). The user row is locked next, as supply
     * creation locks it before checking the owner's membership: a supply created concurrently is either seen here,
     * and refuses the deletion, or finds the user gone.
     */
    public void delete(UserId id) {
        lockPlatformAdminsRepository.lock();
        if (!lockUserRepository.lock(id)) {
            throw new UserNotFoundException(id);
        }
        getUserRepository.findById(id)
                .filter(user -> LastPlatformAdminRule.blocksRevocation(user,
                        platformAdminRepository::countPlatformAdmins))
                .ifPresent(user -> {
                    throw new LastPlatformAdminException();
                });
        if (getSupplyRepository.existsByOwner(id)) {
            throw new UserOwnsSuppliesException(id);
        }
        // community_memberships.user_id has no ON DELETE rule: the memberships go first, their tokens with them
        deleteMembershipRepository.deleteByUser(id);
        repository.delete(id);
    }
}
