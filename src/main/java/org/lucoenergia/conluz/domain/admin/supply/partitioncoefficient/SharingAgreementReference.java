package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;

import java.util.UUID;

/**
 * The identifying and display data of the sharing agreement that authored a coefficient, as carried
 * by {@link SupplyPartitionCoefficientDetail}. Not the {@code SharingAgreement} aggregate, for the
 * reason given on {@link SupplyReference}.
 */
public record SharingAgreementReference(UUID id, String name, SharingAgreementStatus status) {
}
