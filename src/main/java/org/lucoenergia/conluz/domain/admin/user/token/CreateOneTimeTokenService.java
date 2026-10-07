package org.lucoenergia.conluz.domain.admin.user.token;

import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.shared.UserId;

/**
 * Issues secret, single-use, expiring tokens to a user for one {@link OneTimeTokenPurpose}.
 *
 * <p>A user has at most one active (unused and unrevoked) token per purpose: issuing a new one revokes the earlier
 * ones. Issuing locks the user row before the token rows; see {@link ConsumeOneTimeTokenService} for the order a
 * caller that consumes a token must follow. All token times come from the application {@link java.time.Clock}.</p>
 */
public interface CreateOneTimeTokenService {

    /**
     * Issues a token for the user and purpose, revoking every earlier unused, unrevoked token of the same user and
     * purpose.
     *
     * @return the token, which is not stored and cannot be retrieved again
     * @throws UserNotFoundException if the user does not exist
     */
    RawOneTimeToken issue(UserId userId, OneTimeTokenPurpose purpose);
}
