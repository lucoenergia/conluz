package org.lucoenergia.conluz.domain.admin.community.membership.accept;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.time.Instant;
import java.util.UUID;

public interface AcceptMembershipRepository {

    /**
     * Records when the user's membership in the community was accepted. No other column is written.
     *
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException if there is no such
     *         membership
     */
    void accept(UserId userId, UUID communityId, Instant acceptedAt);
}
