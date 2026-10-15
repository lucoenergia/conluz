package org.lucoenergia.conluz.domain.admin.community.membership.accept;

/**
 * The invitation is for an account that already has a password, so it can only be accepted after logging in. Its
 * token is left usable.
 */
public class InvitationLoginRequiredException extends RuntimeException {

    public InvitationLoginRequiredException() {
        super("The invitation must be accepted after logging in");
    }
}
