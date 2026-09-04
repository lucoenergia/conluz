package org.lucoenergia.conluz.domain.production.sharingagreement;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile.SharingAgreementFileSummary;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SharingAgreementTest {

    /**
     * Guards against the exact failure shape a hand-written copy-wither is prone to: a field added to
     * {@link SharingAgreement} without also being added to {@link SharingAgreement#withFile}'s builder
     * chain would compile fine, break no getter-based assertion written before that field existed, and
     * silently vanish specifically on every path that calls {@code withFile}. Iterating declared fields
     * via reflection (rather than asserting a fixed list of getters) keeps this check live as fields are
     * added later, without relying on this test being remembered and updated at the same time.
     */
    @Test
    void withFilePreservesEveryOtherField() throws IllegalAccessException {
        SharingAgreement original = new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withPlantId(UUID.randomUUID())
                .withName("Reparto 2025-2026")
                .withNotes("Adjusted after member B joined")
                .withStatus(SharingAgreementStatus.PUBLISHED)
                .withInstalledPowerKw(BigDecimal.valueOf(12.5))
                .withCreatedAt(Instant.now())
                .withCreatedBy(UUID.randomUUID())
                .build();

        SharingAgreementFileSummary newFile = new SharingAgreementFileSummary.Builder()
                .withId(UUID.randomUUID())
                .withFilename("distributor-2025.txt")
                .withUploadedAt(Instant.now())
                .build();

        SharingAgreement result = original.withFile(newFile);

        for (Field field : SharingAgreement.class.getDeclaredFields()) {
            field.setAccessible(true);
            if (field.getName().equals("file")) {
                continue;
            }
            assertEquals(field.get(original), field.get(result),
                    "withFile must preserve field '" + field.getName() + "'");
        }
        assertSame(newFile, result.getFile());
    }
}
