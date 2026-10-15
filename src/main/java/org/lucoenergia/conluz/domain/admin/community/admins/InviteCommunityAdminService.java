package org.lucoenergia.conluz.domain.admin.community.admins;

import org.lucoenergia.conluz.domain.admin.community.membership.MemberRecord;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;

import java.util.UUID;

/**
 * Invites a community admin on behalf of the platform admin (ADR-0003 §3), in one operation: registers the person as
 * a member is registered, with the community admin role, and sends them the invitation to accept it.
 *
 * <p>The outcome is the same whether or not the account existed, and nothing about the account is written, returned
 * or logged.</p>
 */
public interface InviteCommunityAdminService {

    /**
     * @return the pending admin membership, with its invitation instant and when the link sent expires
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipInvitationUnavailableException if the server
     *         cannot send the invitation: the public web URL is not configured, or email sending is disabled. Checked
     *         first, and nothing is created
     * @throws org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException if the community does not
     *         exist
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyExistsException if the person already
     *         has a membership in the community, whatever its role or state. Nothing is created or sent
     */
    CommunityAdmin invite(UUID communityId, UserId invitedBy, UserPersonalId personalId, MemberRecord record);
}
