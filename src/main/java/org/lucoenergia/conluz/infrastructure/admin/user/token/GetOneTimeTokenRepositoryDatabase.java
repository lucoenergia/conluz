package org.lucoenergia.conluz.infrastructure.admin.user.token;

import org.lucoenergia.conluz.domain.admin.user.token.GetOneTimeTokenRepository;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenMembership;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Transactional(readOnly = true)
@Repository
public class GetOneTimeTokenRepositoryDatabase implements GetOneTimeTokenRepository {

    private final OneTimeTokenJpaRepository jpaRepository;

    public GetOneTimeTokenRepositoryDatabase(OneTimeTokenJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<UserId> findValidOwner(String tokenHash, OneTimeTokenPurpose purpose, Instant now) {
        return jpaRepository.findValidUserId(tokenHash, purpose, now).map(UserId::of);
    }

    @Override
    public long countCreatedSince(UserId userId, OneTimeTokenPurpose purpose, Instant since) {
        return jpaRepository.countCreatedSince(userId.getId(), purpose, since);
    }

    @Override
    public Optional<OneTimeTokenMembership> findValidMembership(String tokenHash, OneTimeTokenPurpose purpose,
                                                                Instant now) {
        return jpaRepository.findValidMembership(tokenHash, purpose, now).map(OneTimeTokenMembershipView::toDomain);
    }

    @Override
    public long countCreatedForMembershipSince(UserId userId, UUID communityId, OneTimeTokenPurpose purpose,
                                               Instant since) {
        return jpaRepository.countCreatedForMembershipSince(userId.getId(), communityId, purpose, since);
    }

    @Override
    public Optional<Instant> findEarliestCreatedForMembershipSince(UserId userId, UUID communityId,
                                                                   OneTimeTokenPurpose purpose, Instant since) {
        return Optional.ofNullable(jpaRepository.findEarliestCreatedForMembershipSince(userId.getId(), communityId,
                purpose, since));
    }

    @Override
    public Optional<Instant> findValidExpiryOfMembership(UserId userId, UUID communityId,
                                                         OneTimeTokenPurpose purpose, Instant now) {
        return jpaRepository.findValidExpiryOfMembership(userId.getId(), communityId, purpose, now);
    }

    @Override
    public Map<UUID, Instant> findValidExpiriesInCommunity(UUID communityId, OneTimeTokenPurpose purpose,
                                                           Instant now) {
        // At most one valid token per membership: one_time_token_membership_one_active_uq allows one active token
        return jpaRepository.findValidExpiriesInCommunity(communityId, purpose, now).stream()
                .collect(Collectors.toMap(OneTimeTokenExpiryView::getMembershipId,
                        OneTimeTokenExpiryView::getExpiresAt));
    }
}
