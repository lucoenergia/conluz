package org.lucoenergia.conluz.domain.admin.user.token;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads one-time tokens without changing them.
 */
public interface GetOneTimeTokenService {

    /**
     * Finds the user a token belongs to, if the token is currently valid for the purpose, without consuming or
     * locking it. See {@link ConsumeOneTimeTokenService} for the lock ordering this serves.
     *
     * @param rawToken the token as received; may be null, empty or malformed
     * @return the token's user, or empty for any token that is not valid for this purpose, without telling why
     * @throws IllegalArgumentException if the purpose is bound to a membership
     */
    Optional<UserId> findOwner(String rawToken, OneTimeTokenPurpose purpose);

    /**
     * Counts the tokens of the purpose issued for the user at or after {@code since}, whatever their state now.
     *
     * @throws IllegalArgumentException if {@code since} is further back than {@link OneTimeTokenRetention#PERIOD},
     *                                  where tokens may already have been deleted and the count would be short, or
     *                                  if the purpose is bound to a membership
     */
    long countIssuedSince(UserId userId, OneTimeTokenPurpose purpose, Instant since);

    /**
     * Finds the membership a token was issued for, if the token is currently valid for the purpose, without
     * consuming or locking it. See {@link ConsumeOneTimeTokenService} for the lock ordering this serves.
     *
     * @param rawToken the token as received; may be null, empty or malformed
     * @return the token's membership, or empty for any token that is not valid for this purpose, without telling why
     * @throws IllegalArgumentException if the purpose is bound only to a user
     */
    Optional<OneTimeTokenMembership> findMembership(String rawToken, OneTimeTokenPurpose purpose);

    /**
     * Counts the tokens of the purpose issued for the user's membership in the community at or after
     * {@code since}, whatever their state now. Tokens issued for the user's other memberships are not counted.
     *
     * @return the count, zero if the user has no membership in the community
     * @throws IllegalArgumentException if {@code since} is further back than {@link OneTimeTokenRetention#PERIOD},
     *                                  where tokens may already have been deleted and the count would be short, or
     *                                  if the purpose is bound only to a user
     */
    long countIssuedForMembershipSince(UserId userId, UUID communityId, OneTimeTokenPurpose purpose, Instant since);

    /**
     * Finds when the oldest token of the purpose issued for the user's membership in the community at or after
     * {@code since} was issued, whatever its state now: the tokens {@link #countIssuedForMembershipSince} counts.
     *
     * @return the oldest such token's creation instant, empty if there is none or the user has no membership in
     * the community
     * @throws IllegalArgumentException if {@code since} is further back than {@link OneTimeTokenRetention#PERIOD},
     *                                  where tokens may already have been deleted, or if the purpose is bound only to
     *                                  a user
     */
    Optional<Instant> findEarliestIssuedForMembershipSince(UserId userId, UUID communityId,
                                                           OneTimeTokenPurpose purpose, Instant since);

    /**
     * Finds when the valid token of the purpose of the user's membership in the community expires.
     *
     * @return the expiry, empty if the membership has no valid token of the purpose (none issued, or used, revoked
     * or expired) or the user has no membership in the community
     * @throws IllegalArgumentException if the purpose is bound only to a user
     */
    Optional<Instant> findValidExpiryForMembership(UserId userId, UUID communityId, OneTimeTokenPurpose purpose);

    /**
     * Finds, in one query, when the valid token of the purpose of each membership of the community expires.
     *
     * @return the expiries by membership id; a membership without a valid token of the purpose is absent
     * @throws IllegalArgumentException if the purpose is bound only to a user
     */
    Map<UUID, Instant> findValidExpiriesInCommunity(UUID communityId, OneTimeTokenPurpose purpose);

    /**
     * The platform-admin invitation of a valid token, without consuming or locking anything.
     *
     * @return empty if the token is null, blank, unknown, of another purpose, used, revoked or expired
     * @throws IllegalArgumentException if the purpose is not bound to a platform-admin invitation
     */
    Optional<OneTimeTokenPlatformAdminInvitation> findPlatformAdminInvitation(String rawToken,
                                                                              OneTimeTokenPurpose purpose);

    /**
     * How many tokens of the purpose were issued for the invitation since an instant, whatever their state.
     *
     * @throws IllegalArgumentException if the purpose is not bound to a platform-admin invitation, or the instant
     *         is before the retention period
     */
    long countIssuedForPlatformAdminInvitationSince(UUID invitationId, OneTimeTokenPurpose purpose, Instant since);

    /**
     * When the earliest of the tokens counted by {@link #countIssuedForPlatformAdminInvitationSince} was issued.
     *
     * @throws IllegalArgumentException as {@link #countIssuedForPlatformAdminInvitationSince}
     */
    Optional<Instant> findEarliestIssuedForPlatformAdminInvitationSince(UUID invitationId,
                                                                        OneTimeTokenPurpose purpose, Instant since);
}
