package org.lucoenergia.conluz.domain.production.sharingagreement.get;

import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementNotFoundException;
import org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile.SharingAgreementPlantMismatchException;

import java.util.List;
import java.util.UUID;

/**
 * Service for retrieving a sharing agreement's full, per-supply partition-coefficient set.
 */
public interface GetSharingAgreementPartitionCoefficientsService {

    /**
     * The full coefficient set of {@code sharingAgreementId}, one row per supply, ordered by CUPS
     * ascending. Works regardless of the agreement's status (DRAFT, PUBLISHED or SUPERSEDED).
     *
     * @throws SharingAgreementNotFoundException     if no such agreement exists
     * @throws SharingAgreementPlantMismatchException if the agreement does not belong to plantId
     */
    List<SharingAgreementCoefficient> findBySharingAgreementId(UUID plantId, UUID sharingAgreementId);
}
