package org.lucoenergia.conluz.domain.admin.user.token;

import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.time.Instant;
import java.util.UUID;

public interface CreateOneTimeTokenRepository {

    /**
     * Locks the user row, revokes every unused, unrevoked token of the user and purpose at {@code createdAt}, and
     * stores the new token, in one transaction. Locking the user row first serialises concurrent issuers for the
     * same user, so at most one active token remains. Callers that also lock token rows must lock the user row
     * before them (see {@link ConsumeOneTimeTokenService}).
     *
     * @throws UserNotFoundException if the user does not exist
     */
    void replaceActive(UserId userId, OneTimeTokenPurpose purpose, String tokenHash, Instant createdAt,
                       Instant expiresAt);

    /**
     * Locks the user row, then the user's membership row in the community, revokes the membership's unused,
     * unrevoked token at {@code createdAt}, and stores the new token for that membership, in one transaction. Only
     * that membership's token is revoked: the user's tokens for other memberships stay valid. Locking the user and
     * membership rows first serialises concurrent issuers for the same membership, so at most one active token
     * remains. The lock order is user row, then membership row, then token rows (see
     * {@link ConsumeOneTimeTokenService}).
     *
     * @throws MembershipNotFoundException if the user has no membership in the community
     */
    void replaceActiveForMembership(UserId userId, UUID communityId, OneTimeTokenPurpose purpose, String tokenHash,
                                    Instant createdAt, Instant expiresAt);
}
