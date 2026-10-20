package org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation;

/**
 * The server cannot send a platform-admin invitation right now: the public web URL is not configured, or email
 * sending is disabled. Nothing has been looked up or written.
 */
public class PlatformAdminInvitationUnavailableException extends RuntimeException {

    public PlatformAdminInvitationUnavailableException() {
        super("Platform-admin invitations cannot be sent right now");
    }
}
