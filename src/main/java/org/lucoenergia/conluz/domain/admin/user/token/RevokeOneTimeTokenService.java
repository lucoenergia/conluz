package org.lucoenergia.conluz.domain.admin.user.token;

import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.UUID;

/**
 * Revokes one-time tokens issued by {@link CreateOneTimeTokenService} before they are used or expire.
 *
 * <p>Revoking locks the user row, then the membership row, before the token rows; see
 * {@link ConsumeOneTimeTokenService} for the order. All token times come from the application
 * {@link java.time.Clock}.</p>
 */
public interface RevokeOneTimeTokenService {

    /**
     * Revokes the active (unused and unrevoked) token of the purpose issued for the user's membership in the
     * community, so it can no longer be used. The user's tokens for other memberships are left untouched. Revoking
     * a membership without an active token changes nothing.
     *
     * @return {@code true} if a token was revoked, {@code false} if there was no active token to revoke
     * @throws MembershipNotFoundException if the user has no membership in the community
     * @throws IllegalArgumentException    if the purpose is bound only to a user
     */
    boolean revokeForMembership(UserId userId, UUID communityId, OneTimeTokenPurpose purpose);

    /**
     * Revokes the active token of the purpose issued for the invitation, locking the user row, then the invitation
     * row, first.
     *
     * @return whether a token was revoked
     * @throws IllegalArgumentException if the purpose is not bound to a platform-admin invitation
     */
    boolean revokeForPlatformAdminInvitation(UserId userId, UUID invitationId, OneTimeTokenPurpose purpose);
}
