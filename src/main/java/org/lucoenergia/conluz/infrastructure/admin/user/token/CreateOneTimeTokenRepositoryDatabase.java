package org.lucoenergia.conluz.infrastructure.admin.user.token;

import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.token.CreateOneTimeTokenRepository;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Transactional
@Repository
public class CreateOneTimeTokenRepositoryDatabase implements CreateOneTimeTokenRepository {

    private final OneTimeTokenJpaRepository jpaRepository;

    public CreateOneTimeTokenRepositoryDatabase(OneTimeTokenJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void replaceActive(UserId userId, OneTimeTokenPurpose purpose, String tokenHash, Instant createdAt,
                              Instant expiresAt) {
        // The user row first, then the token rows: a concurrent issuer for the same user waits here until this
        // transaction commits, and then revokes the token inserted below
        if (jpaRepository.lockUser(userId.getId()).isEmpty()) {
            throw new UserNotFoundException(userId);
        }
        jpaRepository.revokeActive(userId.getId(), purpose, createdAt);
        jpaRepository.saveAndFlush(new OneTimeTokenEntity(UUID.randomUUID(), userId.getId(), purpose, tokenHash,
                createdAt, expiresAt));
    }

    @Override
    public void replaceActiveForMembership(UserId userId, UUID communityId, OneTimeTokenPurpose purpose,
                                           String tokenHash, Instant createdAt, Instant expiresAt) {
        // The user row, then the membership row, then the token rows: a concurrent issuer for the same membership
        // waits here until this transaction commits, and then revokes the token inserted below. A user without the
        // membership is reported as a missing membership, since that is what the caller asked for
        UUID membershipId = jpaRepository.lockUser(userId.getId())
                .flatMap(user -> jpaRepository.lockMembership(userId.getId(), communityId))
                .orElseThrow(() -> new MembershipNotFoundException(communityId, userId.getId()));
        jpaRepository.revokeActiveOfMembership(membershipId, createdAt);
        jpaRepository.saveAndFlush(new OneTimeTokenEntity(UUID.randomUUID(), userId.getId(), membershipId, purpose,
                tokenHash, createdAt, expiresAt));
    }

    @Override
    public void replaceActiveForPlatformAdminInvitation(UserId userId, UUID invitationId, OneTimeTokenPurpose purpose,
                                                        String tokenHash, Instant createdAt, Instant expiresAt) {
        // The user row, then the invitation row, then the token rows, as for a membership
        if (jpaRepository.lockUser(userId.getId()).isEmpty()) {
            throw new UserNotFoundException(userId);
        }
        if (jpaRepository.lockPlatformAdminInvitation(invitationId).isEmpty()) {
            throw new IllegalStateException("The platform-admin invitation " + invitationId + " does not exist");
        }
        jpaRepository.revokeActiveOfPlatformAdminInvitation(invitationId, createdAt);
        jpaRepository.saveAndFlush(OneTimeTokenEntity.forPlatformAdminInvitation(UUID.randomUUID(), userId.getId(),
                invitationId, purpose, tokenHash, createdAt, expiresAt));
    }
}
