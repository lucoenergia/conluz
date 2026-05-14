package org.lucoenergia.conluz.domain.admin.supply.get;

import org.lucoenergia.conluz.domain.admin.supply.SharingAgreement;

import java.util.List;

public interface GetAllSharingAgreementsRepository {

    List<SharingAgreement> findAll();
}
