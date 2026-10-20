package org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation;

/**
 * The platform-admin invitation is for an account that already has a password, so it can only be accepted after
 * logging in. Its token is left usable, and its code is not looked at.
 */
public class PlatformAdminInvitationLoginRequiredException extends RuntimeException {

    public PlatformAdminInvitationLoginRequiredException() {
        super("The platform-admin invitation must be accepted after logging in");
    }
}
