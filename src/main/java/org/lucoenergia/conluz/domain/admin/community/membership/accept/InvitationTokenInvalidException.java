package org.lucoenergia.conluz.domain.admin.community.membership.accept;

/**
 * An invitation token that cannot be used, for whatever reason. The reason is deliberately not told, and the token
 * is never part of the message.
 */
public class InvitationTokenInvalidException extends RuntimeException {

    public InvitationTokenInvalidException() {
        super("The invitation token is not valid");
    }
}
