package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.invitation;

import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.CreatePlatformAdminInvitationRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Repository
@Transactional
public class CreatePlatformAdminInvitationRepositoryDatabase implements CreatePlatformAdminInvitationRepository {

    private final PlatformAdminInvitationJpaRepository jpaRepository;

    public CreatePlatformAdminInvitationRepositoryDatabase(PlatformAdminInvitationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void create(UUID invitationId, UserId userId, String email, String fullName, UserId invitedBy,
                       Instant invitedAt, String confirmationCodeHash) {
        jpaRepository.saveAndFlush(new PlatformAdminInvitationEntity(invitationId, userId.getId(), email, fullName,
                invitedBy.getId(), invitedAt, confirmationCodeHash));
    }
}
