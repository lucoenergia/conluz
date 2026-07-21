package org.lucoenergia.conluz.domain.production.plant.sharingagreement;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SharingAgreementTest {

    @Test
    void assertDraft_doesNotThrow_whenStatusIsDraft() {
        SharingAgreement agreement = new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withStatus(SharingAgreementStatus.DRAFT)
                .build();

        assertDoesNotThrow(agreement::assertDraft);
    }

    @Test
    void assertDraft_throws_whenStatusIsPublished() {
        UUID id = UUID.randomUUID();
        SharingAgreement agreement = new SharingAgreement.Builder()
                .withId(id)
                .withStatus(SharingAgreementStatus.PUBLISHED)
                .build();

        SharingAgreementNotDraftException exception =
                assertThrows(SharingAgreementNotDraftException.class, agreement::assertDraft);
        assertEquals(id, exception.getId());
        assertEquals(SharingAgreementStatus.PUBLISHED, exception.getCurrentStatus());
    }

    @Test
    void assertDraft_throws_whenStatusIsSuperseded() {
        SharingAgreement agreement = new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withStatus(SharingAgreementStatus.SUPERSEDED)
                .build();

        assertThrows(SharingAgreementNotDraftException.class, agreement::assertDraft);
    }
}
