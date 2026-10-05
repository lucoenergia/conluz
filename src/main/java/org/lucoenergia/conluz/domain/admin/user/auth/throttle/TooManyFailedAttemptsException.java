package org.lucoenergia.conluz.domain.admin.user.auth.throttle;

/**
 * The account or the client address has too many recent failed authentication attempts, so the attempt is
 * refused before the password is checked.
 */
public class TooManyFailedAttemptsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyFailedAttemptsException(long retryAfterSeconds) {
        super("Too many failed authentication attempts");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * @return the whole number of seconds, rounded up, until an attempt will be accepted again
     */
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
