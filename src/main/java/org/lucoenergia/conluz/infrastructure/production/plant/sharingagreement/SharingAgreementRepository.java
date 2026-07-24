package org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement;

import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
