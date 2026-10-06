package org.lucoenergia.conluz.domain.admin.user.token;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.time.Instant;
import java.util.Optional;

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
}
