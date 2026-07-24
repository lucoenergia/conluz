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

    @Test
    void assertNotDraft_doesNotThrow_whenStatusIsPublished() {
        SharingAgreement agreement = new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withStatus(SharingAgreementStatus.PUBLISHED)
                .build();

        assertDoesNotThrow(agreement::assertNotDraft);
    }

    @Test
    void assertNotDraft_doesNotThrow_whenStatusIsSuperseded() {
        // Regression test: a coefficient belonging to an already-SUPERSEDED agreement must remain
        // correctable (e.g. fixing a mis-recorded date on one of its own rows). SUPERSEDED is a
        // recomputed status, not an authored one -- freezing it here would make such corrections
        // permanently impossible.
        SharingAgreement agreement = new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withStatus(SharingAgreementStatus.SUPERSEDED)
                .build();

        assertDoesNotThrow(agreement::assertNotDraft);
    }

    @Test
    void assertNotDraft_throws_whenStatusIsDraft() {
        UUID id = UUID.randomUUID();
        SharingAgreement agreement = new SharingAgreement.Builder()
                .withId(id)
                .withStatus(SharingAgreementStatus.DRAFT)
                .build();

        SharingAgreementNotPublishedException exception =
                assertThrows(SharingAgreementNotPublishedException.class, agreement::assertNotDraft);
        assertEquals(id, exception.getId());
        assertEquals(SharingAgreementStatus.DRAFT, exception.getCurrentStatus());
    }
}
