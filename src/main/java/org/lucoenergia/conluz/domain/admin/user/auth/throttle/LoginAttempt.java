package org.lucoenergia.conluz.domain.admin.user.auth.throttle;

/**
 * A login attempt admitted by the throttling. It holds a slot on the account's and the client address's counters
 * until it is settled: {@link #failed} counts it, {@link #succeeded} resets the account, and {@link #close}
 * releases the slot of an attempt that was settled by neither, such as one that ended in an unexpected error.
 * Use it in a try-with-resources block so the slot is always released.
 */
public interface LoginAttempt extends AutoCloseable {

    /**
     * Counts the attempt as a failure against the account and the client address, and logs it.
     *
     * @param reason why the login failed; logged only
     */
    void failed(LoginFailureReason reason);

    /**
     * Resets the account's counter. The client address's counter is never reset.
     */
    void succeeded();

    /**
     * Releases the attempt's slot without counting it, unless it was already settled.
     */
    @Override
    void close();
}
