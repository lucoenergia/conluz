package org.lucoenergia.conluz.domain.admin.user.password.reset;

import org.lucoenergia.conluz.domain.admin.user.auth.throttle.TooManyFailedAttemptsException;

import java.time.Duration;

/**
 * Starts a password recovery: emails a single-use link to set a new password to the user with the given personal
 * ID (#362).
 *
 * <p>The link is sent only to an enabled user with an email address who has been sent fewer than
 * {@link #DAILY_LIMIT} links in the last {@link #DAILY_WINDOW}. Whatever the case, the call returns the same way,
 * so the caller cannot tell whether a link was sent, nor whether the personal ID exists.</p>
 */
public interface RequestPasswordResetService {

    int DAILY_LIMIT = 3;
    Duration DAILY_WINDOW = Duration.ofDays(1);

    /**
     * @param personalId the personal ID as submitted, before normalisation
     * @param clientIp   the address of the client
     * @throws TooManyFailedAttemptsException if the client address is throttled
     */
    void request(String personalId, String clientIp);
}
