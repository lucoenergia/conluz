package org.lucoenergia.conluz.infrastructure.admin.user.token;

import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenMembership;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.UUID;

/**
 * The membership of a stored one-time token, as read by {@link OneTimeTokenJpaRepository}.
 */
public interface OneTimeTokenMembershipView {

    UUID getMembershipId();

    UUID getUserId();

    UUID getCommunityId();

    default OneTimeTokenMembership toDomain() {
        return new OneTimeTokenMembership(getMembershipId(), UserId.of(getUserId()), getCommunityId());
    }
}
