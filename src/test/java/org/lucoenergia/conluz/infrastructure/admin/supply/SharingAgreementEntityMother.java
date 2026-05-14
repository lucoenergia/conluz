package org.lucoenergia.conluz.infrastructure.admin.supply;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class SharingAgreementEntityMother {

    public static SharingAgreementEntity random() {
        SharingAgreementEntity entity = new SharingAgreementEntity();
        entity.setId(UUID.randomUUID());
        entity.setStartDate(LocalDate.now());
        entity.setEndDate(LocalDate.now().plusMonths(6));
        entity.setNotes(null);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    public static SharingAgreementEntity withId(UUID id) {
        SharingAgreementEntity entity = random();
        entity.setId(id);
        return entity;
    }

    public static SharingAgreementEntity withStartDate(LocalDate startDate) {
        SharingAgreementEntity entity = random();
        entity.setStartDate(startDate);
        return entity;
    }

    public static SharingAgreementEntity withEndDate(LocalDate endDate) {
        SharingAgreementEntity entity = random();
        entity.setEndDate(endDate);
        return entity;
    }
}
