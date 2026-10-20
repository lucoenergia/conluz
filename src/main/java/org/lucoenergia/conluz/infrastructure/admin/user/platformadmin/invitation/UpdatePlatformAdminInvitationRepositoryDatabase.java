package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.invitation;

import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.UpdatePlatformAdminInvitationRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Each write reads the row first: callers hold its lock, so what is read is what is written over.
 */
@Repository
@Transactional
public class UpdatePlatformAdminInvitationRepositoryDatabase implements UpdatePlatformAdminInvitationRepository {

    private final PlatformAdminInvitationJpaRepository jpaRepository;

    public UpdatePlatformAdminInvitationRepositoryDatabase(PlatformAdminInvitationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void replaceConfirmationCode(UUID invitationId, String confirmationCodeHash) {
        PlatformAdminInvitationEntity invitation = read(invitationId);
        invitation.replaceConfirmationCode(confirmationCodeHash);
        jpaRepository.saveAndFlush(invitation);
    }

    @Override
    public int recordFailedCodeAttempt(UUID invitationId) {
        PlatformAdminInvitationEntity invitation = read(invitationId);
        int attempts = invitation.recordFailedCodeAttempt();
        jpaRepository.saveAndFlush(invitation);
        return attempts;
    }

    @Override
    public void markAccepted(UUID invitationId, Instant acceptedAt) {
        PlatformAdminInvitationEntity invitation = read(invitationId);
        invitation.markAccepted(acceptedAt);
        jpaRepository.saveAndFlush(invitation);
    }

    private PlatformAdminInvitationEntity read(UUID invitationId) {
        return jpaRepository.findById(invitationId)
                .orElseThrow(() -> new IllegalStateException("The platform-admin invitation " + invitationId
                        + " does not exist"));
    }
}
