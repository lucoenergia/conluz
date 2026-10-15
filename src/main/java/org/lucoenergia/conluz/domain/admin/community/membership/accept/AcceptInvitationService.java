package org.lucoenergia.conluz.domain.admin.community.membership.accept;

import org.lucoenergia.conluz.domain.shared.UserId;

/**
 * Accepts the pending membership an invitation link was sent for (#394). Accepting proves the person, never the
 * link alone (ADR-0003 §6): an account without a password proves it by choosing its first password, and an account
 * with one by logging in.
 */
public interface AcceptInvitationService {

    /**
     * Accepts the invitation of an account without a password, setting its first password. When the account has
     * no email, the member record's contact email becomes its email.
     *
     * @param rawToken    the token from the invitation link
     * @param newPassword the account's first password
     * @param clientIp    the address of the client
     * @throws InvitationTokenInvalidException if the token is not a usable invitation token
     * @throws InvitationLoginRequiredException if the account has a password: it must log in to accept
     * @throws org.lucoenergia.conluz.domain.admin.user.password.PasswordPolicyViolationException if the new password
     *         breaks the password policy
     * @throws org.lucoenergia.conluz.domain.admin.user.auth.throttle.TooManyFailedAttemptsException if the client
     *         address is throttled
     */
    void acceptWithNewPassword(String rawToken, String newPassword, String clientIp);

    /**
     * Accepts the invitation of one of the caller's own memberships. The password is neither read nor changed.
     *
     * @param caller   the authenticated user
     * @param rawToken the token from the invitation link
     * @param clientIp the address of the client
     * @throws InvitationTokenInvalidException if the token is not a usable invitation token of the caller
     * @throws org.lucoenergia.conluz.domain.admin.user.auth.throttle.TooManyFailedAttemptsException if the client
     *         address is throttled
     */
    void acceptAsUser(UserId caller, String rawToken, String clientIp);
}
