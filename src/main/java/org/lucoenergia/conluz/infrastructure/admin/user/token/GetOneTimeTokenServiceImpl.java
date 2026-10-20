package org.lucoenergia.conluz.infrastructure.admin.user.token;

import org.lucoenergia.conluz.domain.admin.user.token.GetOneTimeTokenRepository;
import org.lucoenergia.conluz.domain.admin.user.token.GetOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenMembership;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPlatformAdminInvitation;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenRetention;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
@Service
public class GetOneTimeTokenServiceImpl implements GetOneTimeTokenService {

    private final GetOneTimeTokenRepository repository;
    private final Clock clock;

    public GetOneTimeTokenServiceImpl(GetOneTimeTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public Optional<UserId> findOwner(String rawToken, OneTimeTokenPurpose purpose) {
        purpose.requireBoundOnlyToUser();
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return repository.findValidOwner(OneTimeTokenHash.of(rawToken), purpose, clock.instant());
    }

    @Override
    public long countIssuedSince(UserId userId, OneTimeTokenPurpose purpose, Instant since) {
        purpose.requireBoundOnlyToUser();
        requireWithinRetention(since);
        return repository.countCreatedSince(userId, purpose, since);
    }

    @Override
    public Optional<OneTimeTokenMembership> findMembership(String rawToken, OneTimeTokenPurpose purpose) {
        purpose.requireBoundToMembership();
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return repository.findValidMembership(OneTimeTokenHash.of(rawToken), purpose, clock.instant());
    }

    @Override
    public long countIssuedForMembershipSince(UserId userId, UUID communityId, OneTimeTokenPurpose purpose,
                                              Instant since) {
        purpose.requireBoundToMembership();
        requireWithinRetention(since);
        return repository.countCreatedForMembershipSince(userId, communityId, purpose, since);
    }

    @Override
    public Optional<Instant> findEarliestIssuedForMembershipSince(UserId userId, UUID communityId,
                                                                  OneTimeTokenPurpose purpose, Instant since) {
        purpose.requireBoundToMembership();
        requireWithinRetention(since);
        return repository.findEarliestCreatedForMembershipSince(userId, communityId, purpose, since);
    }

    @Override
    public Optional<Instant> findValidExpiryForMembership(UserId userId, UUID communityId,
                                                          OneTimeTokenPurpose purpose) {
        purpose.requireBoundToMembership();
        return repository.findValidExpiryOfMembership(userId, communityId, purpose, clock.instant());
    }

    @Override
    public Map<UUID, Instant> findValidExpiriesInCommunity(UUID communityId, OneTimeTokenPurpose purpose) {
        purpose.requireBoundToMembership();
        return repository.findValidExpiriesInCommunity(communityId, purpose, clock.instant());
    }

    @Override
    public Optional<OneTimeTokenPlatformAdminInvitation> findPlatformAdminInvitation(String rawToken,
                                                                                     OneTimeTokenPurpose purpose) {
        purpose.requireBoundToPlatformAdminInvitation();
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return repository.findValidPlatformAdminInvitation(OneTimeTokenHash.of(rawToken), purpose, clock.instant());
    }

    @Override
    public long countIssuedForPlatformAdminInvitationSince(UUID invitationId, OneTimeTokenPurpose purpose,
                                                           Instant since) {
        purpose.requireBoundToPlatformAdminInvitation();
        requireWithinRetention(since);
        return repository.countCreatedForPlatformAdminInvitationSince(invitationId, purpose, since);
    }

    @Override
    public Optional<Instant> findEarliestIssuedForPlatformAdminInvitationSince(UUID invitationId,
                                                                               OneTimeTokenPurpose purpose,
                                                                               Instant since) {
        purpose.requireBoundToPlatformAdminInvitation();
        requireWithinRetention(since);
        return repository.findEarliestCreatedForPlatformAdminInvitationSince(invitationId, purpose, since);
    }

    private void requireWithinRetention(Instant since) {
        if (since.isBefore(clock.instant().minus(OneTimeTokenRetention.PERIOD))) {
            throw new IllegalArgumentException("Tokens are only counted within the retention period of "
                    + OneTimeTokenRetention.PERIOD);
        }
    }
}
