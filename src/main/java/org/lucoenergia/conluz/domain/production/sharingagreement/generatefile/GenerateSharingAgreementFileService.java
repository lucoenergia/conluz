package org.lucoenergia.conluz.domain.production.sharingagreement.generatefile;

import org.lucoenergia.conluz.domain.production.plant.PlantMissingRegulatoryCodeException;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementCoefficientSumInvalidException;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementNotFoundException;

import java.util.UUID;

/**
 * Builds the i-DE distributor coefficient-partition file for a sharing agreement on demand, from
 * its current (active) partition coefficients. Nothing is persisted.
 */
public interface GenerateSharingAgreementFileService {

    /**
     * @param year used only to build the filename ({@code {regulatoryCode}_{year}.txt}); it is
     *             never encoded in the file content
     * @throws SharingAgreementNotFoundException            if no such agreement exists, or it does
     *                                                       not belong to {@code plantId}
     * @throws PlantMissingRegulatoryCodeException          if the plant has no regulatory code
     *                                                       (CAU) configured
     * @throws SharingAgreementCoefficientSumInvalidException if the agreement's active partition
     *                                                       coefficients do not sum to exactly 1
     */
    GeneratedDistributorFile generate(UUID plantId, UUID sharingAgreementId, int year);
}
