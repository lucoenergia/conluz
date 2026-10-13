package org.lucoenergia.conluz.domain.admin.user.token;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.Optional;

/**
 * Consumes one-time tokens issued by {@link CreateOneTimeTokenService}.
 *
 * <p><b>Lock ordering.</b> Always lock the user row, then the membership row, then the token rows.
 * {@link CreateOneTimeTokenService} does so itself: it locks the user row, and the membership row for a token issued
 * for a membership, then revokes and inserts tokens; {@link RevokeOneTimeTokenService} does the same. A flow that
 * consumes a token and then writes the user (for example, setting a new password) must take the locks in the same
 * order, or it can deadlock against a concurrent issue for the same user. In one transaction:</p>
 * <ol>
 *     <li>{@link GetOneTimeTokenService#findOwner} to learn which user the token belongs to, without locking
 *     anything;</li>
 *     <li>lock that user's row;</li>
 *     <li>{@link #consume}, and proceed only if it returns a user equal to the owner: the token may have been used or
 *     revoked in between.</li>
 * </ol>
 * <p>A flow that consumes a token issued for a membership and then writes the user or the membership follows the
 * same order, with the membership row between the user row and the token. In one transaction:</p>
 * <ol>
 *     <li>{@link GetOneTimeTokenService#findMembership} to learn which membership the token belongs to, without
 *     locking anything;</li>
 *     <li>lock that membership's user row, then the membership row
 *     ({@link org.lucoenergia.conluz.domain.admin.community.membership.lock.LockMembershipRepository});</li>
 *     <li>{@link #consumeForMembership}, and proceed only if it returns a membership equal to the one found: the
 *     token may have been used or revoked in between.</li>
 * </ol>
 */
public interface ConsumeOneTimeTokenService {

    /**
     * Consumes a token: if it is of the given purpose, unused, unrevoked and unexpired, marks it used and returns
     * its user. Marking it used is atomic, so a token is consumed at most once.
     *
     * @param rawToken the token as received; may be null, empty or malformed
     * @return the token's user, or empty for any token that is not valid for this purpose, without telling why
     * @throws IllegalArgumentException if the purpose is bound to a membership
     */
    Optional<UserId> consume(String rawToken, OneTimeTokenPurpose purpose);

    /**
     * Consumes a token issued for a membership: if it is of the given purpose, unused, unrevoked and unexpired, marks
     * it used and returns its membership. Marking it used is atomic, so a token is consumed at most once.
     *
     * @param rawToken the token as received; may be null, empty or malformed
     * @return the token's membership, or empty for any token that is not valid for this purpose, without telling why
     * @throws IllegalArgumentException if the purpose is bound only to a user
     */
    Optional<OneTimeTokenMembership> consumeForMembership(String rawToken, OneTimeTokenPurpose purpose);
}
