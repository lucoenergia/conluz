package org.lucoenergia.conluz.infrastructure.admin.user.token;

import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.admin.user.token.RevokeOneTimeTokenRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Transactional
@Repository
public class RevokeOneTimeTokenRepositoryDatabase implements RevokeOneTimeTokenRepository {

    private final OneTimeTokenJpaRepository jpaRepository;

    public RevokeOneTimeTokenRepositoryDatabase(OneTimeTokenJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public boolean revokeActiveForMembership(UserId userId, UUID communityId, OneTimeTokenPurpose purpose,
                                             Instant now) {
        // The user row, then the membership row, then the token rows, as when issuing
        UUID membershipId = jpaRepository.lockUser(userId.getId())
                .flatMap(user -> jpaRepository.lockMembership(userId.getId(), communityId))
                .orElseThrow(() -> new MembershipNotFoundException(communityId, userId.getId()));
        return jpaRepository.revokeActiveOfMembershipAndPurpose(membershipId, purpose, now) > 0;
    }

    @Override
    public boolean revokeActiveForPlatformAdminInvitation(UserId userId, UUID invitationId,
                                                          OneTimeTokenPurpose purpose, Instant now) {
        // The user row, then the invitation row, then the token rows, as when issuing
        if (jpaRepository.lockUser(userId.getId()).isEmpty()
                || jpaRepository.lockPlatformAdminInvitation(invitationId).isEmpty()) {
            throw new IllegalStateException("The platform-admin invitation " + invitationId + " does not exist");
        }
        return jpaRepository.revokeActiveOfPlatformAdminInvitationAndPurpose(invitationId, purpose, now) > 0;
    }
}
