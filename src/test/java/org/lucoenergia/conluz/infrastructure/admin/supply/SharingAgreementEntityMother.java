package org.lucoenergia.conluz.infrastructure.admin.supply;

import java.time.LocalDate;
import java.util.UUID;

public class SharingAgreementEntityMother {

    public static SharingAgreementEntity random() {
        SharingAgreementEntity entity = new SharingAgreementEntity();
        entity.setId(UUID.randomUUID());
        entity.setStartDate(LocalDate.now());
        entity.setEndDate(LocalDate.now().plusYears(1));
        return entity;
    }
}