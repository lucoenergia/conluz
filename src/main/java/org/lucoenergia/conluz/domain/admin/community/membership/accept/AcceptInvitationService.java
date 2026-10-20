package org.lucoenergia.conluz.domain.admin.community.membership.accept;

import org.lucoenergia.conluz.domain.shared.UserId;

/**
 * Accepts the pending membership an invitation link was sent for (#394), or the platform-admin invitation it was
 * sent for (ADR-0004 §5). Accepting proves the person, never the link alone (ADR-0003 §6): an account without a
 * password proves it by choosing its first password, and an account with one by logging in. A platform-admin
 * invitation also requires its confirmation code, given out of band by whoever invited (ADR-0004 §4).
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
    default void acceptWithNewPassword(String rawToken, String newPassword, String clientIp) {
        acceptWithNewPassword(rawToken, newPassword, null, clientIp);
    }

    /**
     * As {@link #acceptWithNewPassword(String, String, String)}, with the confirmation code a platform-admin
     * invitation requires; a community invitation ignores it. For a platform-admin invitation, the role is granted,
     * and the invitation's destination email becomes the account's email when it has none.
     *
     * @param confirmationCode the code given by whoever invited, or null
     * @throws org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.ConfirmationCodeInvalidException if
     *         the code of a platform-admin invitation is missing or wrong; the attempt is counted against the
     *         invitation, and nothing else changes
     * @throws org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.ConfirmationCodeAttemptsExhaustedException
     *         if it is the fifth such attempt; the invitation's link is revoked
     */
    void acceptWithNewPassword(String rawToken, String newPassword, String confirmationCode, String clientIp);

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
    default void acceptAsUser(UserId caller, String rawToken, String clientIp) {
        acceptAsUser(caller, rawToken, null, clientIp);
    }

    /**
     * As {@link #acceptAsUser(UserId, String, String)}, with the confirmation code a platform-admin invitation
     * requires; a community invitation ignores it. For a platform-admin invitation, the role is granted, and the
     * invitation's destination email becomes the account's email when it has none.
     *
     * @param confirmationCode the code given by whoever invited, or null
     * @throws org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.ConfirmationCodeInvalidException as
     *         {@link #acceptWithNewPassword(String, String, String, String)}
     * @throws org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.ConfirmationCodeAttemptsExhaustedException
     *         as {@link #acceptWithNewPassword(String, String, String, String)}
     */
    void acceptAsUser(UserId caller, String rawToken, String confirmationCode, String clientIp);
}
