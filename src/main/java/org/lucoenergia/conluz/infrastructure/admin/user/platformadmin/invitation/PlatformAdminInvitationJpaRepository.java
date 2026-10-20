package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.invitation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlatformAdminInvitationJpaRepository extends JpaRepository<PlatformAdminInvitationEntity, UUID> {

    /**
     * Locks the account's pending invitation until the end of the transaction. {@code NO KEY UPDATE} conflicts with
     * itself, so concurrent callers for the same invitation run one after another, but not with the
     * {@code KEY SHARE} lock that the foreign key check of a new token takes. Lock the account's user row first.
     *
     * @return the invitation's id, or empty if the account has no pending invitation
     */
    @Query(value = "SELECT id FROM platform_admin_invitation WHERE user_id = :userId AND accepted_at IS NULL "
            + "FOR NO KEY UPDATE", nativeQuery = true)
    Optional<UUID> lockPendingOfUser(@Param("userId") UUID userId);

    /**
     * As {@link #lockPendingOfUser}, for one invitation whatever its state.
     */
    @Query(value = "SELECT id FROM platform_admin_invitation WHERE id = :invitationId FOR NO KEY UPDATE",
            nativeQuery = true)
    Optional<UUID> lockById(@Param("invitationId") UUID invitationId);

    Optional<PlatformAdminInvitationEntity> findFirstByUserIdAndAcceptedAtIsNull(UUID userId);

    /**
     * The pending invitations whose account does not hold the role, with the expiry of their valid token: at most
     * one, since one_time_token_platform_admin_invitation_one_active_uq allows a single active token per invitation.
     */
    @Query(value = "SELECT i.user_id AS userId, i.full_name AS fullName, i.email AS email, "
            + "i.invited_at AS invitedAt, t.expires_at AS linkExpiresAt "
            + "FROM platform_admin_invitation i "
            + "JOIN users u ON u.id = i.user_id "
            + "LEFT JOIN one_time_token t ON t.platform_admin_invitation_id = i.id "
            + "AND t.used_at IS NULL AND t.revoked_at IS NULL AND t.expires_at > :now "
            + "WHERE i.accepted_at IS NULL AND u.is_platform_admin = false "
            + "AND (CAST(:userId AS UUID) IS NULL OR i.user_id = :userId) "
            + "ORDER BY i.full_name, i.user_id", nativeQuery = true)
    List<PendingPlatformAdminView> findPendingRows(@Param("userId") UUID userId, @Param("now") Instant now);
}
