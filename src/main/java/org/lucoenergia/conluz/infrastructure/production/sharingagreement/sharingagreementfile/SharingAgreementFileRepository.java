package org.lucoenergia.conluz.infrastructure.production.sharingagreement.sharingagreementfile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SharingAgreementFileRepository extends JpaRepository<SharingAgreementFileEntity, UUID> {

    Optional<SharingAgreementFileEntity> findFirstBySharingAgreementIdOrderByUploadedAtDesc(UUID sharingAgreementId);

    /**
     * Metadata-only projection of the latest file for one agreement, breaking ties on {@code id} so
     * it agrees with {@link #findLatestSummariesBySharingAgreementIds}.
     */
    Optional<SharingAgreementFileSummaryProjection> findFirstProjectedBySharingAgreementIdOrderByUploadedAtDescIdDesc(
            UUID sharingAgreementId);

    /**
     * Metadata-only projection of the latest file per agreement, for the given ids, in a single
     * query -- used to enrich a list of agreements without issuing one lookup per row. If two files
     * for the same agreement tie exactly on {@code uploadedAt}, both rows are returned by this query;
     * callers must break that tie deterministically themselves (e.g. by id) when building a map keyed
     * by agreement id.
     */
    @Query("SELECT f.id AS id, f.sharingAgreement.id AS sharingAgreementId, f.filename AS filename, f.uploadedAt AS uploadedAt " +
            "FROM sharing_agreement_file f " +
            "WHERE f.uploadedAt = (SELECT MAX(f2.uploadedAt) FROM sharing_agreement_file f2 WHERE f2.sharingAgreement.id = f.sharingAgreement.id) " +
            "AND f.sharingAgreement.id IN :sharingAgreementIds")
    List<SharingAgreementFileSummaryProjection> findLatestSummariesBySharingAgreementIds(
            @Param("sharingAgreementIds") Collection<UUID> sharingAgreementIds);
}
