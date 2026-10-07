package org.lucoenergia.conluz.domain.admin.user.token;

import java.time.Duration;

/**
 * How long a one-time token is kept after it expired, was used or was revoked, before the cleanup deletes it.
 *
 * <p>Counting the tokens issued since an instant is only exact while every token issued since then is still
 * stored, so a count may look back at most this far.</p>
 */
public final class OneTimeTokenRetention {

    public static final Duration PERIOD = Duration.ofDays(7);

    private OneTimeTokenRetention() {
    }
}
