package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.admin.supply.sharingagreement.GetAllSharingAgreementsRepository;
import org.lucoenergia.conluz.domain.admin.supply.sharingagreement.GetSharingAgreementRepository;
import org.lucoenergia.conluz.domain.admin.supply.sharingagreement.OverlappingSharingAgreementException;
import org.lucoenergia.conluz.domain.admin.supply.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.update.UpdateSharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.sharingagreement.create.CreateSharingAgreementServiceImpl;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CreateSharingAgreementServiceTest {

    private final CreateSharingAgreementRepository repository = mock(CreateSharingAgreementRepository.class);
    private final GetAllSharingAgreementsRepository getAllSharingAgreementsRepository = mock(GetAllSharingAgreementsRepository.class);
    private final GetSharingAgreementRepository getSharingAgreementRepository = mock(GetSharingAgreementRepository.class);
    private final UpdateSharingAgreementRepository updateSharingAgreementRepository = mock(UpdateSharingAgreementRepository.class);
    private final CreateSharingAgreementServiceImpl service =
            new CreateSharingAgreementServiceImpl(repository, getAllSharingAgreementsRepository, getSharingAgreementRepository, updateSharingAgreementRepository);

    @Test
    void testCreateWithValidDatesWhenNoActiveAgreementShouldReturnSharingAgreement() {
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        LocalDate endDate = LocalDate.of(2025, 12, 31);
        CreateSharingAgreement command = new CreateSharingAgreement(startDate, endDate, null);

        SharingAgreement mockAgreement = new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withStartDate(startDate)
                .withEndDate(endDate)
                .build();

        when(getSharingAgreementRepository.findFirstByEndDateIsNull()).thenReturn(Optional.empty());
        when(getAllSharingAgreementsRepository.findAll()).thenReturn(List.of());
        when(repository.create(command)).thenReturn(mockAgreement);

        SharingAgreement result = service.create(command);

        assertNotNull(result);
        assertEquals(startDate, result.getStartDate());
        assertEquals(endDate, result.getEndDate());
        verify(repository, times(1)).create(command);
        verify(updateSharingAgreementRepository, never()).update(any(), any(), any());
    }

    @Test
    void testCreateWithEndDateBeforeStartDateShouldThrowIllegalArgumentException() {
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 12, 31);
        CreateSharingAgreement newAgreement = new CreateSharingAgreement(startDate, endDate, null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                service.create(newAgreement));

        assertEquals("Start date must be before end date", exception.getMessage());
        verify(repository, never()).create(any());
    }

    @Test
    void testCreateWithEndDateNullWhenNoActiveAgreementShouldReturnSharingAgreement() {
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        CreateSharingAgreement command = new CreateSharingAgreement(startDate, null, null);

        SharingAgreement mockAgreement = new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withStartDate(startDate)
                .withEndDate(null)
                .build();

        when(getSharingAgreementRepository.findFirstByEndDateIsNull()).thenReturn(Optional.empty());
        when(getAllSharingAgreementsRepository.findAll()).thenReturn(List.of());
        when(repository.create(command)).thenReturn(mockAgreement);

        SharingAgreement result = service.create(command);

        assertNotNull(result);
        assertEquals(startDate, result.getStartDate());
        assertNull(result.getEndDate());
        verify(repository, times(1)).create(command);
    }

    @Test
    void testCreateClosesActiveAgreement() {
        LocalDate activeStartDate = LocalDate.of(2024, 6, 1);
        UUID activeId = UUID.randomUUID();
        SharingAgreement activeAgreement = new SharingAgreement.Builder()
                .withId(activeId)
                .withStartDate(activeStartDate)
                .build();

        LocalDate newStartDate = LocalDate.of(2025, 1, 1);
        LocalDate newEndDate = LocalDate.of(2025, 12, 31);
        CreateSharingAgreement newAgreement = new CreateSharingAgreement(newStartDate, newEndDate, null);

        SharingAgreement mockAgreement = new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withStartDate(newStartDate)
                .withEndDate(newEndDate)
                .build();

        when(getSharingAgreementRepository.findFirstByEndDateIsNull()).thenReturn(Optional.of(activeAgreement));
        when(getAllSharingAgreementsRepository.findAll()).thenReturn(List.of(activeAgreement));
        when(repository.create(newAgreement)).thenReturn(mockAgreement);

        SharingAgreement result = service.create(newAgreement);

        assertNotNull(result);
        assertEquals(LocalDate.of(2024, 12, 31), activeAgreement.getEndDate());
        verify(updateSharingAgreementRepository, times(1)).update(activeAgreement.getId(),
                newAgreement.getStartDate(), newAgreement.getStartDate().minusDays(1));
        verify(repository, times(1)).create(newAgreement);
    }

    @Test
    void testCreateWhenNewStartsBeforeActiveShouldThrowOverlap() {
        UUID activeId = UUID.randomUUID();
        SharingAgreement activeAgreement = new SharingAgreement.Builder()
                .withId(activeId)
                .withStartDate(LocalDate.of(2025, 6, 1))
                .build();

        CreateSharingAgreement newAgreement = new CreateSharingAgreement(
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), null);

        when(getSharingAgreementRepository.findFirstByEndDateIsNull()).thenReturn(Optional.of(activeAgreement));

        assertThrows(OverlappingSharingAgreementException.class, () -> service.create(newAgreement));

        verify(updateSharingAgreementRepository, never()).update(any(), any(), any());
        verify(repository, never()).create(any());
    }

    @Test
    void testCreateWithOverlappingHistoricalAgreementShouldThrowOverlap() {
        UUID historicalId = UUID.randomUUID();
        SharingAgreement historicalAgreement = new SharingAgreement.Builder()
                .withId(historicalId)
                .withStartDate(LocalDate.of(2024, 1, 1))
                .withEndDate(LocalDate.of(2024, 6, 30))
                .build();

        LocalDate newStartDate = LocalDate.of(2024, 3, 1);
        LocalDate newEndDate = LocalDate.of(2024, 9, 30);
        CreateSharingAgreement command = new CreateSharingAgreement(newStartDate, newEndDate, null);

        when(getSharingAgreementRepository.findFirstByEndDateIsNull()).thenReturn(Optional.empty());
        when(getAllSharingAgreementsRepository.findAll()).thenReturn(List.of(historicalAgreement));

        assertThrows(OverlappingSharingAgreementException.class, () -> service.create(command));

        verify(repository, never()).create(any());
    }

    @Test
    void testCreateWithNonOverlappingHistoricalAgreementShouldSucceed() {
        UUID historicalId = UUID.randomUUID();
        SharingAgreement historicalAgreement = new SharingAgreement.Builder()
                .withId(historicalId)
                .withStartDate(LocalDate.of(2024, 1, 1))
                .withEndDate(LocalDate.of(2024, 6, 30))
                .build();

        LocalDate newStartDate = LocalDate.of(2025, 1, 1);
        LocalDate newEndDate = LocalDate.of(2025, 12, 31);
        CreateSharingAgreement newAgreement = new CreateSharingAgreement(newStartDate, newEndDate, null);

        SharingAgreement mockAgreement = new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withStartDate(newStartDate)
                .withEndDate(newEndDate)
                .build();

        when(getSharingAgreementRepository.findFirstByEndDateIsNull()).thenReturn(Optional.empty());
        when(getAllSharingAgreementsRepository.findAll()).thenReturn(List.of(historicalAgreement));
        when(repository.create(newAgreement)).thenReturn(mockAgreement);

        SharingAgreement result = service.create(newAgreement);

        assertNotNull(result);
        assertEquals(newStartDate, result.getStartDate());
        verify(repository, times(1)).create(newAgreement);
    }

    @Test
    void testCreateWithNotesShouldIncludeNotes() {
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        String notes = "Test notes";
        CreateSharingAgreement command = new CreateSharingAgreement(startDate, null, notes);

        SharingAgreement mockAgreement = new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withStartDate(startDate)
                .withEndDate(null)
                .withNotes(notes)
                .build();

        when(getSharingAgreementRepository.findFirstByEndDateIsNull()).thenReturn(Optional.empty());
        when(getAllSharingAgreementsRepository.findAll()).thenReturn(List.of());
        when(repository.create(command)).thenReturn(mockAgreement);

        SharingAgreement result = service.create(command);

        assertNotNull(result);
        assertEquals(notes, result.getNotes());
        verify(repository, times(1)).create(command);
    }
}
