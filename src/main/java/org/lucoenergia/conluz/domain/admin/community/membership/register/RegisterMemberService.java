package org.lucoenergia.conluz.domain.admin.community.membership.register;

import org.lucoenergia.conluz.domain.admin.community.membership.MemberRecord;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;

import java.util.UUID;

/**
 * Registers a member in a community on behalf of one of its admins (ADR-0003 §3): finds the account with the
 * personal id, or creates one without a password, and creates a pending membership with the member record. If the
 * record has a contact email, the member is sent an invitation.
 *
 * <p>The outcome is the same whether or not the account existed, and nothing about the account is written, returned
 * or logged.</p>
 */
public interface RegisterMemberService {

    /**
     * @throws org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException if the community does not
     *         exist
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyExistsException if the person already
     *         has a membership in the community, whatever its state
     */
    RegisteredMember register(UUID communityId, UserId registeredBy, UserPersonalId personalId, MemberRecord record);
}
