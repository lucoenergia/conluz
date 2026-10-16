package org.lucoenergia.conluz.domain.admin.community.membership.record;

import org.lucoenergia.conluz.domain.admin.community.membership.MemberRecord;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.UUID;

/**
 * Writes the member record of a membership: its full name, member number, contact email, address and phone number.
 * Nothing else is written: the account, the membership's role, state and invitation, and the person's other
 * memberships are left as they are.
 */
public interface UpdateMemberRecordRepository {

    /**
     * Replaces the record of the user's membership of the community with {@code record}.
     *
     * @return {@code false} if the user has no membership in the community, and nothing was written
     */
    boolean update(UUID communityId, UserId userId, MemberRecord record);
}
