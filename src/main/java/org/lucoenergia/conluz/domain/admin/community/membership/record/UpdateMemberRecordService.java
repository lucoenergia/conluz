package org.lucoenergia.conluz.domain.admin.community.membership.record;

import org.lucoenergia.conluz.domain.admin.community.membership.MemberRecord;
import org.lucoenergia.conluz.domain.admin.community.membership.MembershipWithInvitation;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.UUID;

/**
 * Replaces the member record a community keeps on one of its memberships (ADR-0003 §1). Only the record is written:
 * never the account, which the person shares with their other communities, nor the membership's role, state or
 * invitation (#416).
 */
public interface UpdateMemberRecordService {

    /**
     * @return the membership with its new record, and when its invitation link expires
     * @throws org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException if the community does not
     *         exist
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException if the user has no membership
     *         in the community
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipWithoutContactEmailException if the membership
     *         has the community admin role and the new record has no contact email; nothing is written
     */
    MembershipWithInvitation update(UUID communityId, UserId userId, MemberRecord record);
}
