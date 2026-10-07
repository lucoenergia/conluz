package org.lucoenergia.conluz.domain.admin.user.auth.throttle;

import org.lucoenergia.conluz.domain.admin.user.User;

/**
 * A password reset admitted by the throttling. It holds a slot on the client address's counter until it is settled:
 * {@link #failed} counts an invalid reset token, {@link #succeeded} resets the account of the user whose password was
 * reset, and {@link #close} releases the slot of a reset that was settled by neither, such as one refused because the
 * new password breaks the policy. Use it in a try-with-resources block so the slot is always released.
 */
public interface PasswordResetAttempt extends AutoCloseable {

    /**
     * Counts an invalid reset token against the client address, and logs it.
     */
    void failed();

    /**
     * Resets the counter of the user's account, so that a user throttled for failed logins can log in at once with
     * the new password. The client address's counter is never reset.
     */
    void succeeded(User user);

    /**
     * Releases the reset's slot without counting it, unless it was already settled.
     */
    @Override
    void close();
}
