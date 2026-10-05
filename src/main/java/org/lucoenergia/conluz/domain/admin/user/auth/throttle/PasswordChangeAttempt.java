package org.lucoenergia.conluz.domain.admin.user.auth.throttle;

/**
 * A password change admitted by the throttling. It holds a slot on the account's and the client address's counters
 * until it is settled: {@link #failed} counts a wrong current password, {@link #succeeded} resets the account, and
 * {@link #close} releases the slot of a change that was settled by neither, such as one refused because the new
 * password breaks the policy. Use it in a try-with-resources block so the slot is always released.
 */
public interface PasswordChangeAttempt extends AutoCloseable {

    /**
     * Counts a wrong current password against the account and the client address, and logs it.
     */
    void failed();

    /**
     * Resets the account's counter. The client address's counter is never reset.
     */
    void succeeded();

    /**
     * Releases the change's slot without counting it, unless it was already settled.
     */
    @Override
    void close();
}
