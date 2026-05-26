package org.lucoenergia.conluz.domain.admin.supply.create;

import org.lucoenergia.conluz.domain.admin.supply.sharingagreement.SharingAgreement;

public interface CreateSharingAgreementRepository {

    SharingAgreement create(CreateSharingAgreement command);
}
