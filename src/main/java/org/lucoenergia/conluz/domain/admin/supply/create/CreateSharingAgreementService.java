package org.lucoenergia.conluz.domain.admin.supply.create;

import org.lucoenergia.conluz.domain.admin.supply.sharingagreement.SharingAgreement;

public interface CreateSharingAgreementService {

    SharingAgreement create(CreateSharingAgreement command);
}
