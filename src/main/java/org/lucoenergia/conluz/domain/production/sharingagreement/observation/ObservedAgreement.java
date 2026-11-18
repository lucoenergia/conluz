package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A sharing agreement as the observation reads it.
 *
 * @param distributorFileSentOn the local day its distributor file was sent, or null
 */
public record ObservedAgreement(UUID id, String name, SharingAgreementStatus status,
                                LocalDate distributorFileSentOn, Instant createdAt) {
}
