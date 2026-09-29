package org.lucoenergia.conluz.domain.production.sharingagreement.get;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;

public interface GetSharingAgreementRepository {

    /**
     * The id of the current PUBLISHED sharing agreement for a plant, if any.
     */
    Optional<UUID> findCurrentPublishedAgreementIdByPlantId(UUID plantId);

    Optional<SharingAgreement> findById(UUID id);

    /**
     * The agreements with the given ids, in one query and in no particular order, <strong>without
     * their file summary</strong>: {@code getFile()} is {@code null} on every result whether or not a
     * file exists. For callers that need the agreement's identity and plant -- an access decision --
     * and not its contents. An id that matches nothing is absent, and an empty collection issues no
     * query.
     */
    List<SharingAgreement> findAllByIdsWithoutFile(Collection<UUID> ids);

    /**
     * Sharing agreements of a plant, newest first. A {@code null} {@code status} means no filter.
     */
    List<SharingAgreement> findByPlantId(UUID plantId, SharingAgreementStatus status);

    /**
     * Whether any non-DRAFT agreement of {@code plantId}, other than {@code excludeSharingAgreementId},
     * was created after {@code afterCreatedAt}. DRAFT agreements are always excluded: a
     * draft-in-progress must never change how an existing, already-published agreement's
     * coefficients are displayed.
     */
    boolean existsLaterNonDraftAgreement(UUID plantId, UUID excludeSharingAgreementId, Instant afterCreatedAt);
}
