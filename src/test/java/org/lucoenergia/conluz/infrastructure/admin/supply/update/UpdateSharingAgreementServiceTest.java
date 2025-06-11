package org.lucoenergia.conluz.infrastructure.admin.supply.update;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.update.UpdateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.admin.supply.update.UpdateSharingAgreementService;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UpdateSharingAgreementServiceTest {

    private final UpdateSharingAgreementRepository repository = mock(UpdateSharingAgreementRepository.class);
    private final UpdateSharingAgreementService service = new UpdateSharingAgreementServiceImpl(repository);

    /**
     * Test case: Updates a sharing agreement successfully when valid inputs are provided.
     */
    @Test
    void testUpdateWithValidInputReturnsSuccessfulUpdate() {
        // Arrange
        UUID id = UUID.randomUUID();
        LocalDate startDate = LocalDate.of(2023, 1, 1);
        LocalDate endDate = LocalDate.of(2023, 12, 31);
        SharingAgreement expectedAgreement = new SharingAgreement(id, startDate, endDate);

        when(repository.update(id, startDate, endDate)).thenReturn(expectedAgreement);

        // Act
        SharingAgreement result = service.update(id, startDate, endDate);

        // Assert
        assertNotNull(result);
        assertEquals(expectedAgreement.getId(), result.getId());
        assertEquals(expectedAgreement.getStartDate(), result.getStartDate());
        assertEquals(expectedAgreement.getEndDate(), result.getEndDate());
        verify(repository, times(1)).update(id, startDate, endDate);
    }

    /**
     * Test case: Throws exception when the start date is after the end date.
     */
    @Test
    void testUpdateWithStartDateAfterEndDateThrowsException() {
        // Arrange
        UUID id = UUID.randomUUID();
        LocalDate startDate = LocalDate.of(2023, 12, 31);
        LocalDate endDate = LocalDate.of(2023, 1, 1);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.update(id, startDate, endDate));
        assertEquals("Start date must be before end date", exception.getMessage());
        verify(repository, never()).update(any(), any(), any());
    }

    /**
     * Test case: Throws exception when the end date is null.
     */
    @Test
    void testUpdateWithNullEndDateSuccessfulUpdate() {
        // Arrange
        UUID id = UUID.randomUUID();
        LocalDate startDate = LocalDate.of(2023, 1, 1);
        SharingAgreement expectedAgreement = new SharingAgreement(id, startDate, null);


        when(repository.update(id, startDate, null)).thenReturn(expectedAgreement);

        // Act
        SharingAgreement result = service.update(id, startDate, null);

        // Assert
        assertNotNull(result);
        assertEquals(expectedAgreement.getId(), result.getId());
        assertEquals(expectedAgreement.getStartDate(), result.getStartDate());
        assertEquals(expectedAgreement.getEndDate(), result.getEndDate());
        verify(repository, times(1)).update(id, startDate, null);
    }
}