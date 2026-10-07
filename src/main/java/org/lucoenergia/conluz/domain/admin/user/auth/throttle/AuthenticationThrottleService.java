package org.lucoenergia.conluz.domain.admin.user.auth.throttle;

import org.lucoenergia.conluz.domain.admin.user.User;

/**
 * Slows down password guessing on the endpoints that check a password: login and password change. Password recovery
 * (#362) uses the client address counter alone: every recovery request counts against it, and so does every reset
 * with an invalid token.
 * <p>
 * Failed attempts are counted twice: per account and per client address. Both endpoints feed the same counters,
 * and the account is identified by its normalised personal ID in both, so failed logins and wrong current
 * passwords on the same account add up. Each counter counts failures in a fixed window that starts at the first
 * counted failure. A success resets the account counter only, never the client address counter.
 * <p>
 * An attempt is admitted only if a slot can be reserved on both counters: while the failures plus the attempts in
 * progress reach either limit, it is refused with {@link TooManyFailedAttemptsException} before the password is
 * checked. Reserving the slot at admission, rather than counting only once the password has been checked, is what
 * keeps concurrent attempts within the limits.
 * <p>
 * Every failure is logged as a single warning without the password or the full personal ID.
 */
public interface AuthenticationThrottleService {

    /**
     * Admits a login attempt, or refuses it while the account or the client address is throttled. The answer is the
     * same whether or not a user with that personal ID exists.
     *
     * @param personalId the personal ID as submitted, before normalisation; may be {@code null}
     * @param clientIp   the address of the client
     * @return the admitted attempt, to be settled with its outcome and closed
     * @throws TooManyFailedAttemptsException if the account or the client address is throttled
     */
    LoginAttempt startLogin(String personalId, String clientIp);

    /**
     * Admits a password change, or refuses it while the user's account or the client address is throttled.
     *
     * @param user     the authenticated user changing their password
     * @param clientIp the address of the client
     * @return the admitted change, to be settled with its outcome and closed
     * @throws TooManyFailedAttemptsException if the account or the client address is throttled
     */
    PasswordChangeAttempt startPasswordChange(User user, String clientIp);

    /**
     * Counts a password recovery request against the client address, whatever its outcome, or refuses it while the
     * client address is throttled. No account counter is involved: the answer must not depend on the personal ID.
     *
     * @param clientIp the address of the client
     * @throws TooManyFailedAttemptsException if the client address is throttled
     */
    void countPasswordResetRequest(String clientIp);

    /**
     * Admits a password reset, or refuses it while the client address is throttled. The account is not known until
     * the reset token has been checked, so only the client address is counted.
     *
     * @param clientIp the address of the client
     * @return the admitted reset, to be settled with its outcome and closed
     * @throws TooManyFailedAttemptsException if the client address is throttled
     */
    PasswordResetAttempt startPasswordReset(String clientIp);
}
