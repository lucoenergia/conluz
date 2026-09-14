package org.lucoenergia.conluz.infrastructure.production.sharingagreement;

import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SharingAgreementRepository extends JpaRepository<SharingAgreementEntity, UUID> {

    Optional<SharingAgreementEntity> findFirstByPlantIdAndStatusOrderByCreatedAtDesc(
            UUID plantId, SharingAgreementStatus status);

    List<SharingAgreementEntity> findByPlantIdOrderByCreatedAtDesc(UUID plantId);

    List<SharingAgreementEntity> findByPlantIdAndStatusOrderByCreatedAtDesc(UUID plantId, SharingAgreementStatus status);

    // Native SQL (not JPQL): reads supply_partition_coefficient directly to decide the status, so it
    // must bypass Hibernate's persistence context -- flushAutomatically forces the batch's own
    // coefficient writes to be visible to the EXISTS subqueries first, and clearAutomatically evicts
    // this entity's now-stale in-memory copy afterward. Never touches DRAFT (WHERE clause), and never
    // stores a "previous status": the CASE recomputes PUBLISHED-vs-SUPERSEDED fresh every time from
    // the coefficient rows, per D4.
    //
    // The ELSE branch defaults anything that isn't SUPERSEDED to PUBLISHED. If SharingAgreementStatus
    // ever grows a fourth value, this query must be revisited -- as written it would silently rewrite
    // that new status back to PUBLISHED.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE sharing_agreement sa SET status = CASE
                WHEN EXISTS (SELECT 1 FROM supply_partition_coefficient c WHERE c.sharing_agreement_id = sa.id)
                     AND NOT EXISTS (SELECT 1 FROM supply_partition_coefficient c
                                     WHERE c.sharing_agreement_id = sa.id AND c.valid_to IS NULL)
                THEN 'SUPERSEDED' ELSE 'PUBLISHED' END
            WHERE sa.id = :sharingAgreementId AND sa.status <> 'DRAFT'
            """, nativeQuery = true)
    void recomputeStatus(@Param("sharingAgreementId") UUID sharingAgreementId);

    // DRAFT is always excluded: a draft-in-progress can be freely edited or deleted before publish
    // and must never change how an existing, already-published agreement's coefficients are displayed.
    @Query("SELECT COUNT(sa) > 0 FROM sharing_agreement sa WHERE sa.plant.id = :plantId " +
            "AND sa.status <> :draftStatus AND sa.createdAt > :afterCreatedAt AND sa.id <> :excludeId")
    boolean existsLaterNonDraftAgreement(@Param("plantId") UUID plantId,
                                          @Param("draftStatus") SharingAgreementStatus draftStatus,
                                          @Param("afterCreatedAt") Instant afterCreatedAt,
                                          @Param("excludeId") UUID excludeId);

    // Native SQL, not read-then-write: folding the PUBLISHED-status and inert (no coefficient has
    // valid_from set) preconditions into the same UPDATE's WHERE clause makes the whole check-and-write
    // atomic under the row lock the UPDATE itself takes, closing the race window a separate "read the
    // gate, then write the status" would leave open (e.g. a concurrent coefficient activation setting
    // valid_from between the read and the write). Returns the number of rows updated (0 or 1); the
    // caller re-reads to classify *why* it was 0 only for error-message purposes, never for correctness.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE sharing_agreement sa SET status = 'DRAFT'
            WHERE sa.id = :sharingAgreementId
              AND sa.plant_id = :plantId
              AND sa.status = 'PUBLISHED'
              AND NOT EXISTS (SELECT 1 FROM supply_partition_coefficient c
                              WHERE c.sharing_agreement_id = sa.id AND c.valid_from IS NOT NULL)
            """, nativeQuery = true)
    int revertToDraftIfEligible(@Param("sharingAgreementId") UUID sharingAgreementId, @Param("plantId") UUID plantId);
}
