package org.lucoenergia.conluz.infrastructure.admin.user.token;

import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.admin.user.token.RevokeOneTimeTokenRepository;
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
public class RevokeOneTimeTokenRepositoryDatabase implements RevokeOneTimeTokenRepository {

    private final OneTimeTokenJpaRepository jpaRepository;
    private final UserRepository userRepository;
    private final CommunityMembershipJpaRepository membershipRepository;
    private final PlatformAdminInvitationJpaRepository invitationRepository;

    public RevokeOneTimeTokenRepositoryDatabase(OneTimeTokenJpaRepository jpaRepository, UserRepository userRepository,
                                                CommunityMembershipJpaRepository membershipRepository,
                                                PlatformAdminInvitationJpaRepository invitationRepository) {
        this.jpaRepository = jpaRepository;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.invitationRepository = invitationRepository;
    }

    @Override
    public boolean revokeActiveForMembership(UserId userId, UUID communityId, OneTimeTokenPurpose purpose,
                                             Instant now) {
        // The user row, then the membership row, then the token rows, as when issuing
        UUID membershipId = userRepository.lockById(userId.getId())
                .flatMap(user -> membershipRepository.lockByUserIdAndCommunityId(userId.getId(), communityId))
                .orElseThrow(() -> new MembershipNotFoundException(communityId, userId.getId()));
        return jpaRepository.revokeActiveOfMembershipAndPurpose(membershipId, purpose, now) > 0;
    }

    @Override
    public boolean revokeActiveForPlatformAdminInvitation(UserId userId, UUID invitationId,
                                                          OneTimeTokenPurpose purpose, Instant now) {
        // The user row, then the invitation row, then the token rows, as when issuing
        if (userRepository.lockById(userId.getId()).isEmpty()
                || invitationRepository.lockById(invitationId).isEmpty()) {
            throw new IllegalStateException("The platform-admin invitation " + invitationId + " does not exist");
        }
        return jpaRepository.revokeActiveOfPlatformAdminInvitationAndPurpose(invitationId, purpose, now) > 0;
    }
}
