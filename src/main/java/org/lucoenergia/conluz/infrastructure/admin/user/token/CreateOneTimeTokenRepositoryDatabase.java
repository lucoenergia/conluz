package org.lucoenergia.conluz.infrastructure.admin.user.token;

import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.token.CreateOneTimeTokenRepository;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.invitation.PlatformAdminInvitationJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Transactional
@Repository
public class CreateOneTimeTokenRepositoryDatabase implements CreateOneTimeTokenRepository {

    private final OneTimeTokenJpaRepository jpaRepository;
    private final UserRepository userRepository;
    private final CommunityMembershipJpaRepository membershipRepository;
    private final PlatformAdminInvitationJpaRepository invitationRepository;

    public CreateOneTimeTokenRepositoryDatabase(OneTimeTokenJpaRepository jpaRepository, UserRepository userRepository,
                                                CommunityMembershipJpaRepository membershipRepository,
                                                PlatformAdminInvitationJpaRepository invitationRepository) {
        this.jpaRepository = jpaRepository;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.invitationRepository = invitationRepository;
    }

    @Override
    public void replaceActive(UserId userId, OneTimeTokenPurpose purpose, String tokenHash, Instant createdAt,
                              Instant expiresAt) {
        // The user row first, then the token rows: a concurrent issuer for the same user waits here until this
        // transaction commits, and then revokes the token inserted below
        if (userRepository.lockById(userId.getId()).isEmpty()) {
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
        UUID membershipId = userRepository.lockById(userId.getId())
                .flatMap(user -> membershipRepository.lockByUserIdAndCommunityId(userId.getId(), communityId))
                .orElseThrow(() -> new MembershipNotFoundException(communityId, userId.getId()));
        jpaRepository.revokeActiveOfMembership(membershipId, createdAt);
        jpaRepository.saveAndFlush(new OneTimeTokenEntity(UUID.randomUUID(), userId.getId(), membershipId, purpose,
                tokenHash, createdAt, expiresAt));
    }

    @Override
    public void replaceActiveForPlatformAdminInvitation(UserId userId, UUID invitationId, OneTimeTokenPurpose purpose,
                                                        String tokenHash, Instant createdAt, Instant expiresAt) {
        // The user row, then the invitation row, then the token rows, as for a membership
        if (userRepository.lockById(userId.getId()).isEmpty()) {
            throw new UserNotFoundException(userId);
        }
        if (invitationRepository.lockById(invitationId).isEmpty()) {
            throw new IllegalStateException("The platform-admin invitation " + invitationId + " does not exist");
        }
        jpaRepository.revokeActiveOfPlatformAdminInvitation(invitationId, createdAt);
        jpaRepository.saveAndFlush(OneTimeTokenEntity.forPlatformAdminInvitation(UUID.randomUUID(), userId.getId(),
                invitationId, purpose, tokenHash, createdAt, expiresAt));
    }
}
