package org.lucoenergia.conluz.domain.admin.supply.get;

import org.lucoenergia.conluz.domain.admin.supply.SharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementId;

import java.util.Optional;

public interface GetSharingAgreementRepository {

    Optional<SharingAgreement> findById(SharingAgreementId id);
}
