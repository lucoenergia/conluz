package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.invitation;

import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.LockPlatformAdminInvitationRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * A lock outlives the statement that takes it only inside the caller's transaction.
 */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class LockPlatformAdminInvitationRepositoryDatabase implements LockPlatformAdminInvitationRepository {

    private final PlatformAdminInvitationJpaRepository jpaRepository;

    public LockPlatformAdminInvitationRepositoryDatabase(PlatformAdminInvitationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public boolean lockPending(UserId userId) {
        return jpaRepository.lockPendingOfUser(userId.getId()).isPresent();
    }

    @Override
    public boolean lock(UUID invitationId) {
        return jpaRepository.lockById(invitationId).isPresent();
    }
}
