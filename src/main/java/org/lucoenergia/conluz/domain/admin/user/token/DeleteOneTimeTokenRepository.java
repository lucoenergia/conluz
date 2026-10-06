package org.lucoenergia.conluz.domain.admin.user.token;

import java.time.Instant;

public interface DeleteOneTimeTokenRepository {

    /**
     * Deletes every token that expired, was used or was revoked before {@code cutoff}.
     *
     * @return the number of tokens deleted
     */
    int deleteFinishedBefore(Instant cutoff);
}
