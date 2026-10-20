package org.lucoenergia.conluz.domain.admin.community.membership.accept;

/**
 * Inspects an invitation link before it is accepted (#394), without using it up.
 */
public interface GetInvitationService {

    /**
     * Tells what the invitation is for: the community of the token's membership, or the platform-admin role; whether
     * its account must log in to accept it; and whether a confirmation code is required. Writes nothing, and leaves
     * the token usable.
     *
     * @param rawToken the token from the invitation link
     * @param clientIp the address of the client
     * @throws InvitationTokenInvalidException if the token is not a usable invitation token
     * @throws org.lucoenergia.conluz.domain.admin.user.auth.throttle.TooManyFailedAttemptsException if the client
     *         address is throttled
     */
    InvitationInspection inspect(String rawToken, String clientIp);
}
