package org.lucoenergia.conluz.domain.admin.user.token;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.Optional;

/**
 * Consumes one-time tokens issued by {@link CreateOneTimeTokenService}.
 *
 * <p><b>Lock ordering.</b> Always lock the user row before that user's token rows.
 * {@link CreateOneTimeTokenService#issue} does so itself: it locks the user row, then revokes and inserts tokens. A
 * flow that consumes a token and then writes the user (for example, setting a new password) must take the locks in
 * the same order, or it can deadlock against a concurrent issue for the same user. In one transaction:</p>
 * <ol>
 *     <li>{@link GetOneTimeTokenService#findOwner} to learn which user the token belongs to, without locking
 *     anything;</li>
 *     <li>lock that user's row;</li>
 *     <li>{@link #consume}, and proceed only if it returns a user equal to the owner: the token may have been used or
 *     revoked in between.</li>
 * </ol>
 */
public interface ConsumeOneTimeTokenService {

    /**
     * Consumes a token: if it is of the given purpose, unused, unrevoked and unexpired, marks it used and returns
     * its user. Marking it used is atomic, so a token is consumed at most once.
     *
     * @param rawToken the token as received; may be null, empty or malformed
     * @return the token's user, or empty for any token that is not valid for this purpose, without telling why
     */
    Optional<UserId> consume(String rawToken, OneTimeTokenPurpose purpose);
}
