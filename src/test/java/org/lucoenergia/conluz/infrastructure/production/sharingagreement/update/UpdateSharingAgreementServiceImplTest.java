package org.lucoenergia.conluz.infrastructure.production.sharingagreement.update;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.update.SharingAgreementDistributorFileSentOnInFutureException;
import org.lucoenergia.conluz.domain.production.sharingagreement.update.UpdateSharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.update.UpdateSharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.update.UpdateSharingAgreementServiceImpl;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateSharingAgreementServiceImplTest {

    @Mock
    private UpdateSharingAgreementRepository repository;
    @Mock
    private ZoneResolver zoneResolver;
    private final Clock clock = Clock.fixed(Instant.parse("2026-03-10T10:00:00Z"), ZoneOffset.UTC);

    private UpdateSharingAgreementServiceImpl service() {
        return new UpdateSharingAgreementServiceImpl(repository, zoneResolver, clock);
    }

    private UpdateSharingAgreement anUpdate() {
        return anUpdate(null);
    }

    private UpdateSharingAgreement anUpdate(LocalDate distributorFileSentOn) {
        return new UpdateSharingAgreement.Builder()
                .withName("name")
                .withNotes("notes")
                .withNominalPowerKW(BigDecimal.TEN)
                .withDistributorFileSentOn(distributorFileSentOn)
                .withUpdatedBy(UUID.randomUUID())
                .build();
    }

    @ParameterizedTest
    @EnumSource(SharingAgreementStatus.class)
    void update_delegatesToRepository_regardlessOfStatus(SharingAgreementStatus status) {
        UUID plantId = UUID.randomUUID();
        UUID agreementId = UUID.randomUUID();
        UpdateSharingAgreement update = anUpdate();
        SharingAgreement updated = new SharingAgreement.Builder().withId(agreementId).withStatus(status).build();
        when(repository.update(plantId, agreementId, update)).thenReturn(updated);

        SharingAgreement result = service().update(plantId, agreementId, update);

        assertEquals(updated, result);
        verify(repository).update(plantId, agreementId, update);
    }

    @Test
    @DisplayName("SHA-001 a distributor file sent date after today in the application time zone is rejected, and nothing is written")
    void update_rejectsAFutureSentDate_withoutWriting() {
        // 2026-03-10T10:00Z is already 2026-03-10 in Madrid; the next day is in the future.
        when(zoneResolver.resolveApplicationZoneId()).thenReturn(ZoneId.of("Europe/Madrid"));
        UpdateSharingAgreement update = anUpdate(LocalDate.of(2026, 3, 11));

        SharingAgreementDistributorFileSentOnInFutureException exception = assertThrows(
                SharingAgreementDistributorFileSentOnInFutureException.class,
                () -> service().update(UUID.randomUUID(), UUID.randomUUID(), update));

        assertEquals(LocalDate.of(2026, 3, 11), exception.getDistributorFileSentOn());
        assertEquals(LocalDate.of(2026, 3, 10), exception.getToday());
        verify(repository, never()).update(any(), any(), any());
    }

    @Test
    @DisplayName("SHA-001 today is resolved in the application time zone, not in UTC")
    void update_acceptsTodayInTheApplicationZone_whenItIsAlreadyTomorrowInUtc() {
        // 2026-03-10T10:00Z is already 2026-03-11 in Pacific/Kiritimati (UTC+14).
        when(zoneResolver.resolveApplicationZoneId()).thenReturn(ZoneId.of("Pacific/Kiritimati"));
        UUID plantId = UUID.randomUUID();
        UUID agreementId = UUID.randomUUID();
        UpdateSharingAgreement update = anUpdate(LocalDate.of(2026, 3, 11));

        service().update(plantId, agreementId, update);

        verify(repository).update(plantId, agreementId, update);
    }

    @Test
    @DisplayName("SHA-001 a sent date of today or earlier is passed on to be stored")
    void update_acceptsTodayAndPastSentDates() {
        when(zoneResolver.resolveApplicationZoneId()).thenReturn(ZoneId.of("Europe/Madrid"));
        UUID plantId = UUID.randomUUID();
        UUID agreementId = UUID.randomUUID();
        UpdateSharingAgreement today = anUpdate(LocalDate.of(2026, 3, 10));
        UpdateSharingAgreement past = anUpdate(LocalDate.of(2025, 1, 1));

        service().update(plantId, agreementId, today);
        service().update(plantId, agreementId, past);

        verify(repository).update(plantId, agreementId, today);
        verify(repository).update(plantId, agreementId, past);
    }

    @Test
    @DisplayName("SHA-001 no sent date is never checked against today, and is passed on to clear the stored one")
    void update_passesANullSentDateOn() {
        UUID plantId = UUID.randomUUID();
        UUID agreementId = UUID.randomUUID();
        UpdateSharingAgreement update = anUpdate(null);

        service().update(plantId, agreementId, update);

        verify(repository).update(plantId, agreementId, update);
        verify(zoneResolver, never()).resolveApplicationZoneId();
    }
}
