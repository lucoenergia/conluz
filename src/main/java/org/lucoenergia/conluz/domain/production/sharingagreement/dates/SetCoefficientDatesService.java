package org.lucoenergia.conluz.domain.production.sharingagreement.dates;

import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementNotPublishedException;
import org.lucoenergia.conluz.domain.production.sharingagreement.activation.CoefficientActivationException;

import java.util.UUID;

/**
 * Sets the application date of each coefficient of an agreement and the closing date of each supply leaving it, in
 * one request (SHA-012, SHA-013).
 */
public interface SetCoefficientDatesService {

    /**
     * Takes the plant's coefficient date lock before reading anything (SHA-007), validates the whole request, then
     * writes every change and recomputes the status of every agreement owning a changed row (SHA-006).
     *
     * @return the effects as written
     * @throws org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException if the plant does not exist
     * @throws org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile.SharingAgreementPlantMismatchException
     *         if the agreement does not belong to {@code plantId}
     * @throws SharingAgreementNotPublishedException if the agreement is DRAFT
     * @throws CoefficientActivationException with every invalid row, when any row is invalid; nothing is written
     */
    CoefficientDatesOutcome setDates(UUID plantId, UUID sharingAgreementId, CoefficientDatesRequest request);
}
