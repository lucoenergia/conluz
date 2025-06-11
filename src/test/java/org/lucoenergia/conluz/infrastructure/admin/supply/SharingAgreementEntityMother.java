package org.lucoenergia.conluz.infrastructure.admin.supply;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Factory for creating SharingAgreementEntity instances for testing
 */
public class SharingAgreementEntityMother {

    /**
     * Creates a random SharingAgreementEntity
     *
     * @return a random SharingAgreementEntity
     */
    public static SharingAgreementEntity random() {
        SharingAgreementEntity entity = new SharingAgreementEntity();
        entity.setId(UUID.randomUUID());
        entity.setStartDate(LocalDate.now());
        entity.setEndDate(LocalDate.now().plusMonths(6));
        return entity;
    }

    /**
     * Creates a SharingAgreementEntity with the specified ID
     *
     * @param id the ID to set
     * @return a SharingAgreementEntity with the specified ID
     */
    public static SharingAgreementEntity withId(UUID id) {
        SharingAgreementEntity entity = random();
        entity.setId(id);
        return entity;
    }

    /**
     * Creates a SharingAgreementEntity with the specified start date
     *
     * @param startDate the start date to set
     * @return a SharingAgreementEntity with the specified start date
     */
    public static SharingAgreementEntity withStartDate(LocalDate startDate) {
        SharingAgreementEntity entity = random();
        entity.setStartDate(startDate);
        return entity;
    }

    /**
     * Creates a SharingAgreementEntity with the specified end date
     *
     * @param endDate the end date to set
     * @return a SharingAgreementEntity with the specified end date
     */
    public static SharingAgreementEntity withEndDate(LocalDate endDate) {
        SharingAgreementEntity entity = random();
        entity.setEndDate(endDate);
        return entity;
    }
}