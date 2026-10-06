package org.lucoenergia.conluz.domain.admin.user.token;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.time.Instant;
import java.util.Optional;

public interface ConsumeOneTimeTokenRepository {

    /**
     * Marks the token with this hash used at {@code now}, in a single conditional update, if it is of the purpose,
     * unused, unrevoked and expires after {@code now}. The update locks the token row: a caller that also writes the
     * token's user must lock the user row first (see {@link OneTimeTokenService}).
     *
     * @return the token's user if this call marked it used, otherwise empty
     */
    Optional<UserId> consume(String tokenHash, OneTimeTokenPurpose purpose, Instant now);
}
