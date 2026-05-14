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

    @Test
    void testUpdateWithValidInputReturnsSuccessfulUpdate() {
        UUID id = UUID.randomUUID();
        LocalDate startDate = LocalDate.of(2023, 1, 1);
        LocalDate endDate = LocalDate.of(2023, 12, 31);
        SharingAgreement expectedAgreement = new SharingAgreement.Builder()
                .withId(id).withStartDate(startDate).withEndDate(endDate).build();

        when(repository.update(id, startDate, endDate)).thenReturn(expectedAgreement);

        SharingAgreement result = service.update(id, startDate, endDate);

        assertNotNull(result);
        assertEquals(expectedAgreement.getId(), result.getId());
        assertEquals(expectedAgreement.getStartDate(), result.getStartDate());
        assertEquals(expectedAgreement.getEndDate(), result.getEndDate());
        verify(repository, times(1)).update(id, startDate, endDate);
    }

    @Test
    void testUpdateWithStartDateAfterEndDateThrowsException() {
        UUID id = UUID.randomUUID();
        LocalDate startDate = LocalDate.of(2023, 12, 31);
        LocalDate endDate = LocalDate.of(2023, 1, 1);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.update(id, startDate, endDate));
        assertEquals("Start date must be before end date", exception.getMessage());
        verify(repository, never()).update(any(), any(), any());
    }

    @Test
    void testUpdateWithNullEndDateSuccessfulUpdate() {
        UUID id = UUID.randomUUID();
        LocalDate startDate = LocalDate.of(2023, 1, 1);
        SharingAgreement expectedAgreement = new SharingAgreement.Builder()
                .withId(id).withStartDate(startDate).withEndDate(null).build();

        when(repository.update(id, startDate, null)).thenReturn(expectedAgreement);

        SharingAgreement result = service.update(id, startDate, null);

        assertNotNull(result);
        assertEquals(expectedAgreement.getId(), result.getId());
        assertEquals(expectedAgreement.getStartDate(), result.getStartDate());
        assertNull(result.getEndDate());
        verify(repository, times(1)).update(id, startDate, null);
    }
}
