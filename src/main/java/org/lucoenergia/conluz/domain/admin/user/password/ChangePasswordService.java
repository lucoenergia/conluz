package org.lucoenergia.conluz.domain.admin.user.password;

import org.lucoenergia.conluz.domain.admin.user.auth.Token;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.TooManyFailedAttemptsException;
import org.lucoenergia.conluz.domain.shared.UserId;

public interface ChangePasswordService {

    /**
     * Replaces a user's own password. The current password must match the stored one, and the new one must
     * satisfy the {@link PasswordPolicy}; it may be equal to the current one.
     * <p>
     * On success the "must change password" flag is cleared and the change instant is recorded, so every token
     * issued before it is rejected from then on. The token used for the change is also revoked explicitly,
     * because a token's issue time only has second precision and may fall within the same second as the change.
     * <p>
     * Wrong current passwords are throttled together with failed logins on the same account, and per client address.
     * A throttled change is refused before the current password is checked, and revokes nothing.
     *
     * @param userId          the user changing their password
     * @param currentPassword the password the user claims to have now
     * @param newPassword     the password to store
     * @param usedToken       the token that authenticated the change request
     * @param clientIp        the address of the client, used for throttling and logging
     * @throws TooManyFailedAttemptsException    if the account or the client address is throttled
     * @throws IncorrectCurrentPasswordException if the current password does not match
     * @throws PasswordPolicyViolationException  if the new password does not satisfy the policy
     */
    void changePassword(UserId userId, String currentPassword, String newPassword, Token usedToken, String clientIp);
}
