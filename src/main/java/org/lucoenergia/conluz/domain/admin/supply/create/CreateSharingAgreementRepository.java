package org.lucoenergia.conluz.domain.admin.supply.create;

import org.lucoenergia.conluz.domain.admin.supply.SharingAgreement;

import java.time.LocalDate;

public interface CreateSharingAgreementRepository {

    SharingAgreement create(LocalDate startDate, LocalDate endDate, String notes);
}
