package org.lucoenergia.conluz.domain.admin.user.token;

import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.UUID;

/**
 * Issues secret, single-use, expiring tokens for one {@link OneTimeTokenPurpose}, either to a user or for one of the
 * user's memberships, depending on {@link OneTimeTokenPurpose#scope()}.
 *
 * <p>A user has at most one active (unused and unrevoked) token per user-bound purpose, and a membership at most one
 * active token: issuing a new one revokes the earlier ones. Issuing locks the user row, then the membership row for
 * a membership-bound purpose, before the token rows; see {@link ConsumeOneTimeTokenService} for the order a caller
 * that consumes a token must follow. All token times come from the application {@link java.time.Clock}.</p>
 */
public interface CreateOneTimeTokenService {

    /**
     * Issues a token for the user and purpose, revoking every earlier unused, unrevoked token of the same user and
     * purpose.
     *
     * @return the token, which is not stored and cannot be retrieved again
     * @throws UserNotFoundException    if the user does not exist
     * @throws IllegalArgumentException if the purpose is bound to a membership
     */
    RawOneTimeToken issue(UserId userId, OneTimeTokenPurpose purpose);

    /**
     * Issues a token for the user's membership in the community, revoking the membership's earlier unused,
     * unrevoked token. The user's tokens for other memberships are left untouched.
     *
     * @return the token, which is not stored and cannot be retrieved again
     * @throws MembershipNotFoundException if the user has no membership in the community
     * @throws IllegalArgumentException    if the purpose is bound only to a user
     */
    RawOneTimeToken issueForMembership(UserId userId, UUID communityId, OneTimeTokenPurpose purpose);

    /**
     * Issues a token for a platform-admin invitation of the user, revoking the invitation's active one. Locks the
     * user row, then the invitation row, before the token rows.
     *
     * @throws IllegalArgumentException if the purpose is not bound to a platform-admin invitation
     */
    RawOneTimeToken issueForPlatformAdminInvitation(UserId userId, UUID invitationId, OneTimeTokenPurpose purpose);
}
