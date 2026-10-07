package org.lucoenergia.conluz.domain.admin.user.password.reset;

import org.lucoenergia.conluz.domain.admin.user.auth.throttle.TooManyFailedAttemptsException;
import org.lucoenergia.conluz.domain.admin.user.password.PasswordPolicyViolationException;
import org.lucoenergia.conluz.domain.admin.user.password.PasswordUnchangedException;

/**
 * Sets a new password with a token emailed by {@link RequestPasswordResetService} (#362).
 *
 * <p>On success the token is used up, the password is replaced, the "must change password" flag is cleared and
 * every session opened before is ended. The user is not logged in. A new password refused by the policy, or equal
 * to the current one, changes nothing and leaves the token usable.</p>
 */
public interface ResetPasswordService {

    /**
     * @param rawToken    the token as received; may be malformed
     * @param newPassword the new password
     * @param clientIp    the address of the client
     * @throws PasswordResetTokenInvalidException if the token is unknown, malformed, expired, used or revoked, or
     *                                            its user is disabled
     * @throws PasswordPolicyViolationException   if the new password breaks the policy
     * @throws PasswordUnchangedException         if the new password is the current one
     * @throws TooManyFailedAttemptsException     if the client address is throttled
     */
    void reset(String rawToken, String newPassword, String clientIp);
}
