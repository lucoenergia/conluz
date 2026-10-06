package org.lucoenergia.conluz.domain.admin.user.token;

import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.time.Instant;
import java.util.Optional;

/**
 * Secret, single-use, expiring tokens issued to a user for one {@link OneTimeTokenPurpose}.
 *
 * <p>A user has at most one active (unused and unrevoked) token per purpose: issuing a new one revokes the earlier
 * ones. All token times come from the application {@link java.time.Clock}.</p>
 *
 * <p><b>Lock ordering.</b> Always lock the user row before that user's token rows. {@link #issue} does so itself:
 * it locks the user row, then revokes and inserts tokens. A flow that consumes a token and then writes the user
 * (for example, setting a new password) must take the locks in the same order, or it can deadlock against a
 * concurrent {@link #issue} for the same user. In one transaction:</p>
 * <ol>
 *     <li>{@link #findOwner} to learn which user the token belongs to, without locking anything;</li>
 *     <li>lock that user's row;</li>
 *     <li>{@link #consume}, and proceed only if it returns the same user: the token may have been used or revoked
 *     in between.</li>
 * </ol>
 */
public interface OneTimeTokenService {

    /**
     * Issues a token for the user and purpose, revoking every earlier unused, unrevoked token of the same user and
     * purpose.
     *
     * @return the token, which is not stored and cannot be retrieved again
     * @throws UserNotFoundException if the user does not exist
     */
    RawOneTimeToken issue(UserId userId, OneTimeTokenPurpose purpose);

    /**
     * Consumes a token: if it is of the given purpose, unused, unrevoked and unexpired, marks it used and returns
     * its user. Marking it used is atomic, so a token is consumed at most once.
     *
     * @param rawToken the token as received; may be null, empty or malformed
     * @return the token's user, or empty for any token that is not valid for this purpose, without telling why
     */
    Optional<UserId> consume(String rawToken, OneTimeTokenPurpose purpose);

    /**
     * Finds the user a token belongs to, if the token is currently valid for the purpose, without consuming or
     * locking it. See the lock ordering above.
     *
     * @param rawToken the token as received; may be null, empty or malformed
     * @return the token's user, or empty for any token that is not valid for this purpose, without telling why
     */
    Optional<UserId> findOwner(String rawToken, OneTimeTokenPurpose purpose);

    /**
     * Counts the tokens of the purpose issued for the user at or after {@code since}, whatever their state now.
     *
     * @throws IllegalArgumentException if {@code since} is further back than {@link OneTimeTokenRetention#PERIOD},
     *                                  where tokens may already have been deleted and the count would be short
     */
    long countIssuedSince(UserId userId, OneTimeTokenPurpose purpose, Instant since);
}
