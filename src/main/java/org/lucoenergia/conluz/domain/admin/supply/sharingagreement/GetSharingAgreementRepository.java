package org.lucoenergia.conluz.domain.admin.supply.sharingagreement;

import org.lucoenergia.conluz.infrastructure.admin.supply.sharingagreement.SharingAgreementEntity;

import java.time.LocalDate;
import java.util.Optional;

public interface GetSharingAgreementRepository {

    Optional<SharingAgreement> findById(SharingAgreementId id);

    Optional<SharingAgreement> findFirstByEndDateIsNull();

    Optional<SharingAgreement> findFirstByEndDate(LocalDate date);
}
