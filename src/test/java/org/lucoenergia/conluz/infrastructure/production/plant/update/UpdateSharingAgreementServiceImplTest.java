package org.lucoenergia.conluz.infrastructure.production.plant.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.production.plant.get.GetSharingAgreementService;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementNotDraftException;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.plant.update.UpdateSharingAgreementRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateSharingAgreementServiceImplTest {

    @Mock
    private GetSharingAgreementService getSharingAgreementService;
    @Mock
    private UpdateSharingAgreementRepository repository;

    private UpdateSharingAgreementServiceImpl service() {
        return new UpdateSharingAgreementServiceImpl(getSharingAgreementService, repository);
    }

    @Test
    void update_throwsNotDraft_whenAgreementIsPublished() {
        UUID agreementId = UUID.randomUUID();
        SharingAgreement published = new SharingAgreement.Builder()
                .withId(agreementId)
                .withStatus(SharingAgreementStatus.PUBLISHED)
                .build();
        when(getSharingAgreementService.findById(agreementId)).thenReturn(published);

        assertThrows(SharingAgreementNotDraftException.class,
                () -> service().update(UUID.randomUUID(), agreementId, "name", "notes", BigDecimal.TEN));
    }

    @Test
    void update_delegatesToRepository_whenAgreementIsDraft() {
        UUID plantId = UUID.randomUUID();
        UUID agreementId = UUID.randomUUID();
        SharingAgreement draft = new SharingAgreement.Builder()
                .withId(agreementId)
                .withStatus(SharingAgreementStatus.DRAFT)
                .build();
        when(getSharingAgreementService.findById(agreementId)).thenReturn(draft);
        SharingAgreement updated = new SharingAgreement.Builder().withId(agreementId).build();
        when(repository.update(plantId, agreementId, "name", "notes", BigDecimal.TEN)).thenReturn(updated);

        SharingAgreement result = service().update(plantId, agreementId, "name", "notes", BigDecimal.TEN);

        assertEquals(updated, result);
        verify(repository).update(plantId, agreementId, "name", "notes", BigDecimal.TEN);
    }
}
