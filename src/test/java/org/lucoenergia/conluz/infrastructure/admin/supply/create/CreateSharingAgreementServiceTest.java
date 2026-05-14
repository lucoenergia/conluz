package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSharingAgreementRepository;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreateSharingAgreementServiceTest {

    private final CreateSharingAgreementRepository repository = mock(CreateSharingAgreementRepository.class);
    private final CreateSharingAgreementServiceImpl service = new CreateSharingAgreementServiceImpl(repository);

    @Test
    void testCreateWithValidDatesShouldReturnSharingAgreement() {
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        LocalDate endDate = LocalDate.of(2025, 12, 31);
        SharingAgreement mockAgreement = new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withStartDate(startDate)
                .withEndDate(endDate)
                .build();

        when(repository.create(startDate, endDate, null)).thenReturn(mockAgreement);

        SharingAgreement result = service.create(startDate, endDate, null);

        assertNotNull(result);
        assertEquals(startDate, result.getStartDate());
        assertEquals(endDate, result.getEndDate());
        verify(repository, times(1)).create(startDate, endDate, null);
    }

    @Test
    void testCreateWithEndDateBeforeStartDateShouldThrowIllegalArgumentException() {
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 12, 31);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                service.create(startDate, endDate, null));

        assertEquals("Start date must be before end date", exception.getMessage());
        verify(repository, never()).create(any(), any(), any());
    }

    @Test
    void testCreateWithEndDateNullShouldReturnSharingAgreement() {
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        SharingAgreement mockAgreement = new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withStartDate(startDate)
                .withEndDate(null)
                .build();

        when(repository.create(startDate, null, null)).thenReturn(mockAgreement);

        SharingAgreement result = service.create(startDate, null, null);

        assertNotNull(result);
        assertEquals(startDate, result.getStartDate());
        assertNull(result.getEndDate());
        verify(repository, times(1)).create(startDate, null, null);
    }
}
