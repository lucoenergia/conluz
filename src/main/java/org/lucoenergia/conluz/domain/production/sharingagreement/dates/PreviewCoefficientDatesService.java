package org.lucoenergia.conluz.domain.production.sharingagreement.dates;

import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementNotPublishedException;
import org.lucoenergia.conluz.domain.production.sharingagreement.activation.CoefficientActivationException;

import java.util.UUID;

/**
 * Computes what {@link SetCoefficientDatesService#setDates} would do with the same request, without writing and
 * without taking the plant's lock (SHA-014).
 */
public interface PreviewCoefficientDatesService {

    /**
     * @return the effects the write would perform on the timeline as it stands now
     * @throws org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile.SharingAgreementPlantMismatchException
     *         if the agreement does not belong to {@code plantId}
     * @throws SharingAgreementNotPublishedException if the agreement is DRAFT
     * @throws CoefficientActivationException with every invalid row, when any row is invalid
     */
    CoefficientDatesOutcome previewDates(UUID plantId, UUID sharingAgreementId, CoefficientDatesRequest request);
}
