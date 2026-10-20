package org.lucoenergia.conluz.domain.admin.user.token;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface GetOneTimeTokenRepository {

    /**
     * The user of the token with this hash, if it is of the purpose, unused, unrevoked and expires after
     * {@code now}. Locks nothing.
     */
    Optional<UserId> findValidOwner(String tokenHash, OneTimeTokenPurpose purpose, Instant now);

    /**
     * Counts the tokens of the user and purpose created at or after {@code since}, whatever their state.
     */
    long countCreatedSince(UserId userId, OneTimeTokenPurpose purpose, Instant since);

    /**
     * The membership of the token with this hash, if it is of the purpose, unused, unrevoked and expires after
     * {@code now}. Locks nothing.
     */
    Optional<OneTimeTokenMembership> findValidMembership(String tokenHash, OneTimeTokenPurpose purpose, Instant now);

    /**
     * Counts the tokens of the purpose created for the user's membership in the community at or after
     * {@code since}, whatever their state. Zero if there is no such membership.
     */
    long countCreatedForMembershipSince(UserId userId, UUID communityId, OneTimeTokenPurpose purpose, Instant since);

    /**
     * The creation instant of the oldest token of the purpose created for the user's membership in the community at
     * or after {@code since}, whatever its state. Empty if there is none, or no such membership.
     */
    Optional<Instant> findEarliestCreatedForMembershipSince(UserId userId, UUID communityId,
                                                            OneTimeTokenPurpose purpose, Instant since);

    /**
     * The expiry of the token of the purpose of the user's membership in the community that is unused, unrevoked
     * and expires after {@code now}. Empty if there is none, or no such membership.
     */
    Optional<Instant> findValidExpiryOfMembership(UserId userId, UUID communityId, OneTimeTokenPurpose purpose,
                                                  Instant now);

    /**
     * The expiries, by membership id, of the tokens of the purpose of the community's memberships that are unused,
     * unrevoked and expire after {@code now}. One query.
     */
    Map<UUID, Instant> findValidExpiriesInCommunity(UUID communityId, OneTimeTokenPurpose purpose, Instant now);

    Optional<OneTimeTokenPlatformAdminInvitation> findValidPlatformAdminInvitation(String tokenHash,
                                                                                   OneTimeTokenPurpose purpose,
                                                                                   Instant now);

    long countCreatedForPlatformAdminInvitationSince(UUID invitationId, OneTimeTokenPurpose purpose, Instant since);

    Optional<Instant> findEarliestCreatedForPlatformAdminInvitationSince(UUID invitationId,
                                                                         OneTimeTokenPurpose purpose, Instant since);
}
