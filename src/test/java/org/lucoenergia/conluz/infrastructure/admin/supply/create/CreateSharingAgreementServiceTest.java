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
        UUID communityId = UUID.randomUUID();
        SharingAgreement mockAgreement = new SharingAgreement(UUID.randomUUID(), startDate, endDate, communityId);

        when(repository.create(startDate, endDate, communityId)).thenReturn(mockAgreement);

        SharingAgreement result = service.create(startDate, endDate, communityId);

        assertNotNull(result);
        assertEquals(startDate, result.getStartDate());
        assertEquals(endDate, result.getEndDate());
        verify(repository, times(1)).create(startDate, endDate, communityId);
    }

    @Test
    void testCreateWithEndDateBeforeStartDateShouldThrowIllegalArgumentException() {
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 12, 31);
        UUID communityId = UUID.randomUUID();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                service.create(startDate, endDate, communityId));

        assertEquals("Start date must be before end date", exception.getMessage());
        verify(repository, never()).create(any(), any(), any());
    }

    @Test
    void testCreateWithEndDateNullShouldReturnSharingAgreement() {
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        UUID communityId = UUID.randomUUID();
        SharingAgreement mockAgreement = new SharingAgreement(UUID.randomUUID(), startDate, null, communityId);

        when(repository.create(startDate, null, communityId)).thenReturn(mockAgreement);

        SharingAgreement result = service.create(startDate, null, communityId);

        assertNotNull(result);
        assertEquals(startDate, result.getStartDate());
        assertNull(result.getEndDate());
        verify(repository, times(1)).create(startDate, null, communityId);
    }
}
