package org.lucoenergia.conluz.domain.production.plant.create;

import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreement;

public interface CreateSharingAgreementRepository {

    SharingAgreement create(SharingAgreement agreement);
}
