package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreement;

import java.time.Instant;

class SharingAgreementEntityMapperTest {

    private final SharingAgreementEntityMapper mapper = new SharingAgreementEntityMapper();

    @Test
    void testMap() {
        SharingAgreementEntity entity = SharingAgreementEntityMother.random();

        SharingAgreement result = mapper.map(entity);

        Assertions.assertEquals(entity.getId(), result.getId());
        Assertions.assertEquals(entity.getStartDate(), result.getStartDate());
        Assertions.assertEquals(entity.getEndDate(), result.getEndDate());
        Assertions.assertEquals(entity.getNotes(), result.getNotes());
        Assertions.assertEquals(entity.getCreatedAt(), result.getCreatedAt());
        Assertions.assertEquals(entity.getUpdatedAt(), result.getUpdatedAt());
    }

    @Test
    void testMapWithSpecificValues() {
        SharingAgreementEntity entity = new SharingAgreementEntity();
        entity.setId(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"));
        entity.setStartDate(java.time.LocalDate.of(2023, 1, 1));
        entity.setEndDate(java.time.LocalDate.of(2023, 12, 31));
        entity.setNotes("Test notes");
        entity.setCreatedAt(Instant.parse("2023-01-01T00:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2023-01-02T00:00:00Z"));

        SharingAgreement result = mapper.map(entity);

        Assertions.assertEquals(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"), result.getId());
        Assertions.assertEquals(java.time.LocalDate.of(2023, 1, 1), result.getStartDate());
        Assertions.assertEquals(java.time.LocalDate.of(2023, 12, 31), result.getEndDate());
        Assertions.assertEquals("Test notes", result.getNotes());
        Assertions.assertEquals(Instant.parse("2023-01-01T00:00:00Z"), result.getCreatedAt());
        Assertions.assertEquals(Instant.parse("2023-01-02T00:00:00Z"), result.getUpdatedAt());
    }
}
