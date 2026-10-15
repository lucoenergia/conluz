package org.lucoenergia.conluz.domain.admin.community.membership.accept;

/**
 * Inspects an invitation link before it is accepted (#394), without using it up.
 */
public interface GetInvitationService {

    /**
     * Tells the community of the token's membership and whether its account must log in to accept it. Writes
     * nothing, and leaves the token usable.
     *
     * @param rawToken the token from the invitation link
     * @param clientIp the address of the client
     * @throws InvitationTokenInvalidException if the token is not a usable invitation token
     * @throws org.lucoenergia.conluz.domain.admin.user.auth.throttle.TooManyFailedAttemptsException if the client
     *         address is throttled
     */
    InvitationInspection inspect(String rawToken, String clientIp);
}
