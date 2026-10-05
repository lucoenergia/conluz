package org.lucoenergia.conluz.domain.admin.user.auth.throttle;

import org.lucoenergia.conluz.domain.admin.user.User;

/**
 * Slows down password guessing on the endpoints that check a password: login and password change.
 * <p>
 * Failed attempts are counted twice: per account and per client address. Both endpoints feed the same counters,
 * and the account is identified by its normalised personal ID in both, so failed logins and wrong current
 * passwords on the same account add up. While either counter is over its limit, every attempt is refused with
 * {@link TooManyFailedAttemptsException} before the password is checked.
 * <p>
 * Each counter counts failures in a fixed window that starts at the first counted failure. A success resets the
 * account counter only, never the client address counter.
 * <p>
 * Every failure is logged as a single warning without the password or the full personal ID.
 */
public interface AuthenticationThrottleService {

    /**
     * Refuses a login attempt while the account or the client address is throttled. The answer is the same
     * whether or not a user with that personal ID exists.
     *
     * @param personalId the personal ID as submitted, before normalisation; may be {@code null}
     * @param clientIp   the address of the client
     * @throws TooManyFailedAttemptsException if the account or the client address is throttled
     */
    void checkLogin(String personalId, String clientIp);

    /**
     * Counts a failed login against the account and the client address, and logs it.
     *
     * @param personalId the personal ID as submitted, before normalisation; may be {@code null}
     * @param clientIp   the address of the client
     * @param reason     why the login failed; logged only
     */
    void loginFailed(String personalId, String clientIp, LoginFailureReason reason);

    /**
     * Resets the account counter after a successful login.
     *
     * @param personalId the personal ID as submitted, before normalisation
     */
    void loginSucceeded(String personalId);

    /**
     * Refuses a password change while the user's account or the client address is throttled.
     *
     * @param user     the authenticated user changing their password
     * @param clientIp the address of the client
     * @throws TooManyFailedAttemptsException if the account or the client address is throttled
     */
    void checkPasswordChange(User user, String clientIp);

    /**
     * Counts a wrong current password against the user's account and the client address, and logs it.
     *
     * @param user     the authenticated user changing their password
     * @param clientIp the address of the client
     */
    void passwordChangeFailed(User user, String clientIp);

    /**
     * Resets the account counter after a successful password change.
     *
     * @param user the user who changed their password
     */
    void passwordChangeSucceeded(User user);
}
