package org.lucoenergia.conluz.domain.admin.user.auth.throttle;

import org.lucoenergia.conluz.domain.admin.user.User;

/**
 * An invitation inspection or acceptance admitted by the throttling (#394). It holds a slot on the client address's
 * counter until it is settled: {@link #failed} counts an unusable invitation token, {@link #succeeded} resets the
 * account of the user who has just set their first password, and {@link #close} releases the slot of an attempt
 * that was settled by neither, such as a successful inspection or a new password that breaks the policy. Use it in
 * a try-with-resources block so the slot is always released.
 */
public interface InvitationAttempt extends AutoCloseable {

    /**
     * Counts an unusable invitation token against the client address, and logs it.
     */
    void failed();

    /**
     * Counts a missing or wrong confirmation code of a platform-admin invitation against the client address, and
     * logs it. The code is never logged.
     */
    void failedConfirmationCode();

    /**
     * Resets the counter of the user's account, so that failed logins counted against the account while it had no
     * password do not keep the person from logging in with the password just set. The client address's counter is
     * never reset.
     */
    void succeeded(User user);

    /**
     * Releases the attempt's slot without counting it, unless it was already settled.
     */
    @Override
    void close();
}
