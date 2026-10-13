package org.lucoenergia.conluz.domain.admin.user.token;

import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.time.Instant;
import java.util.UUID;

public interface RevokeOneTimeTokenRepository {

    /**
     * Locks the user row, then the user's membership row in the community, and revokes the membership's unused,
     * unrevoked token of the purpose at {@code now}, in one transaction. The lock order is user row, then membership
     * row, then token rows (see {@link ConsumeOneTimeTokenService}).
     *
     * @return {@code true} if a token was revoked, {@code false} if the membership had no active token of the purpose
     * @throws MembershipNotFoundException if the user has no membership in the community
     */
    boolean revokeActiveForMembership(UserId userId, UUID communityId, OneTimeTokenPurpose purpose, Instant now);
}
