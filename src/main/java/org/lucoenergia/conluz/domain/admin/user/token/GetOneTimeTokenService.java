package org.lucoenergia.conluz.domain.admin.user.token;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.time.Instant;
import java.util.Optional;

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
