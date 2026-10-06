package org.lucoenergia.conluz.domain.admin.user.token;

import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.time.Instant;

public interface CreateOneTimeTokenRepository {

    /**
     * Locks the user row, revokes every unused, unrevoked token of the user and purpose at {@code createdAt}, and
     * stores the new token, in one transaction. Locking the user row first serialises concurrent issuers for the
     * same user, so at most one active token remains. Callers that also lock token rows must lock the user row
     * before them (see {@link OneTimeTokenService}).
     *
     * @throws UserNotFoundException if the user does not exist
     */
    void replaceActive(UserId userId, OneTimeTokenPurpose purpose, String tokenHash, Instant createdAt,
                       Instant expiresAt);
}
