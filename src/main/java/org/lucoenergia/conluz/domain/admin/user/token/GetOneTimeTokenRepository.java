package org.lucoenergia.conluz.domain.admin.user.token;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.time.Instant;
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
}
