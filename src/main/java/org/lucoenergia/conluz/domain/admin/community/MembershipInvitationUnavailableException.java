package org.lucoenergia.conluz.domain.admin.community;

/**
 * The server cannot send invitations right now: the public web URL the link points to is not configured, or email
 * sending is disabled. Nothing is issued or recorded, so the membership can be invited once the server can send.
 */
public class MembershipInvitationUnavailableException extends RuntimeException {
}
