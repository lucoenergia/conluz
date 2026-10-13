package org.lucoenergia.conluz.domain.admin.user.token;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads one-time tokens without changing them.
 */
public interface GetOneTimeTokenService {

    /**
     * Finds the user a token belongs to, if the token is currently valid for the purpose, without consuming or
     * locking it. See {@link ConsumeOneTimeTokenService} for the lock ordering this serves.
     *
     * @param rawToken the token as received; may be null, empty or malformed
     * @return the token's user, or empty for any token that is not valid for this purpose, without telling why
     * @throws IllegalArgumentException if the purpose is bound to a membership
     */
    Optional<UserId> findOwner(String rawToken, OneTimeTokenPurpose purpose);

    /**
     * Counts the tokens of the purpose issued for the user at or after {@code since}, whatever their state now.
     *
     * @throws IllegalArgumentException if {@code since} is further back than {@link OneTimeTokenRetention#PERIOD},
     *                                  where tokens may already have been deleted and the count would be short, or
     *                                  if the purpose is bound to a membership
     */
    long countIssuedSince(UserId userId, OneTimeTokenPurpose purpose, Instant since);

    /**
     * Finds the membership a token was issued for, if the token is currently valid for the purpose, without
     * consuming or locking it. See {@link ConsumeOneTimeTokenService} for the lock ordering this serves.
     *
     * @param rawToken the token as received; may be null, empty or malformed
     * @return the token's membership, or empty for any token that is not valid for this purpose, without telling why
     * @throws IllegalArgumentException if the purpose is bound only to a user
     */
    Optional<OneTimeTokenMembership> findMembership(String rawToken, OneTimeTokenPurpose purpose);

    /**
     * Counts the tokens of the purpose issued for the user's membership in the community at or after
     * {@code since}, whatever their state now. Tokens issued for the user's other memberships are not counted.
     *
     * @return the count, zero if the user has no membership in the community
     * @throws IllegalArgumentException if {@code since} is further back than {@link OneTimeTokenRetention#PERIOD},
     *                                  where tokens may already have been deleted and the count would be short, or
     *                                  if the purpose is bound only to a user
     */
    long countIssuedForMembershipSince(UserId userId, UUID communityId, OneTimeTokenPurpose purpose, Instant since);
}
