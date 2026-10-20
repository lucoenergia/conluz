package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.invitation;

import org.lucoenergia.conluz.domain.admin.user.platformadmin.PendingPlatformAdmin;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.GetPlatformAdminInvitationRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.PlatformAdminInvitation;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class GetPlatformAdminInvitationRepositoryDatabase implements GetPlatformAdminInvitationRepository {

    private final PlatformAdminInvitationJpaRepository jpaRepository;

    public GetPlatformAdminInvitationRepositoryDatabase(PlatformAdminInvitationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<PlatformAdminInvitation> findPendingByUserId(UserId userId) {
        return jpaRepository.findFirstByUserIdAndAcceptedAtIsNull(userId.getId())
                .map(PlatformAdminInvitationEntity::toDomain);
    }

    @Override
    public Optional<PlatformAdminInvitation> findById(UUID invitationId) {
        return jpaRepository.findById(invitationId).map(PlatformAdminInvitationEntity::toDomain);
    }

    @Override
    public List<PendingPlatformAdmin> findPendingRows(Instant now) {
        return jpaRepository.findPendingRows(null, now).stream().map(PendingPlatformAdminView::toDomain).toList();
    }

    @Override
    public Optional<PendingPlatformAdmin> findPendingRow(UserId userId, Instant now) {
        return jpaRepository.findPendingRows(userId.getId(), now).stream().findFirst()
                .map(PendingPlatformAdminView::toDomain);
    }
}
