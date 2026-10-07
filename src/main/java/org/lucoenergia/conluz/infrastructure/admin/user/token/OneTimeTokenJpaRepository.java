package org.lucoenergia.conluz.infrastructure.admin.user.token;

import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OneTimeTokenJpaRepository extends JpaRepository<OneTimeTokenEntity, UUID> {

    /**
     * Locks the user row until the end of the transaction. {@code NO KEY UPDATE} conflicts with itself, so
     * concurrent callers for the same user run one after another, but not with the {@code KEY SHARE} lock that
     * foreign key checks from other tables take.
     *
     * @return the user's id, or empty if the user does not exist
     */
    @Query(value = "SELECT id FROM users WHERE id = :userId FOR NO KEY UPDATE", nativeQuery = true)
    Optional<UUID> lockUser(@Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE one_time_token t SET t.revokedAt = :now "
            + "WHERE t.userId = :userId AND t.purpose = :purpose AND t.usedAt IS NULL AND t.revokedAt IS NULL")
    int revokeActive(@Param("userId") UUID userId, @Param("purpose") OneTimeTokenPurpose purpose,
                     @Param("now") Instant now);

    @Modifying
    @Query("UPDATE one_time_token t SET t.usedAt = :now "
            + "WHERE t.tokenHash = :tokenHash AND t.purpose = :purpose AND t.usedAt IS NULL AND t.revokedAt IS NULL "
            + "AND t.expiresAt > :now")
    int markUsed(@Param("tokenHash") String tokenHash, @Param("purpose") OneTimeTokenPurpose purpose,
                 @Param("now") Instant now);

    @Query("SELECT t.userId FROM one_time_token t WHERE t.tokenHash = :tokenHash")
    Optional<UUID> findUserIdByTokenHash(@Param("tokenHash") String tokenHash);

    @Query("SELECT t.userId FROM one_time_token t "
            + "WHERE t.tokenHash = :tokenHash AND t.purpose = :purpose AND t.usedAt IS NULL AND t.revokedAt IS NULL "
            + "AND t.expiresAt > :now")
    Optional<UUID> findValidUserId(@Param("tokenHash") String tokenHash, @Param("purpose") OneTimeTokenPurpose purpose,
                                   @Param("now") Instant now);

    @Query("SELECT COUNT(t) FROM one_time_token t "
            + "WHERE t.userId = :userId AND t.purpose = :purpose AND t.createdAt >= :since")
    long countCreatedSince(@Param("userId") UUID userId, @Param("purpose") OneTimeTokenPurpose purpose,
                           @Param("since") Instant since);

    /**
     * The expression matches one_time_token_finished_at_idx. LEAST ignores NULLs, so a token qualifies as soon as
     * any of its expiry, use or revocation is before the cutoff.
     */
    @Modifying
    @Query(value = "DELETE FROM one_time_token WHERE LEAST(expires_at, used_at, revoked_at) < :cutoff",
            nativeQuery = true)
    int deleteFinishedBefore(@Param("cutoff") Instant cutoff);
}
