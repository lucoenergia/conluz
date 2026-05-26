package org.lucoenergia.conluz.domain.admin.supply.sharingagreement;

import java.util.List;

public interface GetAllSharingAgreementsRepository {

    List<SharingAgreement> findAll();
}
