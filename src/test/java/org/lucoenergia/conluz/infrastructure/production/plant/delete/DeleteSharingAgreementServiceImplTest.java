package org.lucoenergia.conluz.infrastructure.production.plant.delete;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.production.plant.delete.DeleteSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.plant.get.GetSharingAgreementService;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementNotDraftException;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementStatus;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteSharingAgreementServiceImplTest {

    @Mock
    private GetSharingAgreementService getSharingAgreementService;
    @Mock
    private DeleteSharingAgreementRepository repository;

    private DeleteSharingAgreementServiceImpl service() {
        return new DeleteSharingAgreementServiceImpl(getSharingAgreementService, repository);
    }

    @Test
    void delete_throwsNotDraft_andNeverDeletes_whenAgreementIsPublished() {
        UUID plantId = UUID.randomUUID();
        UUID agreementId = UUID.randomUUID();
        SharingAgreement published = new SharingAgreement.Builder()
                .withId(agreementId)
                .withStatus(SharingAgreementStatus.PUBLISHED)
                .build();
        when(getSharingAgreementService.findById(agreementId)).thenReturn(published);

        assertThrows(SharingAgreementNotDraftException.class, () -> service().delete(plantId, agreementId));
        verify(repository, never()).delete(plantId, agreementId);
    }

    @Test
    void delete_delegatesToRepository_whenAgreementIsDraft() {
        UUID plantId = UUID.randomUUID();
        UUID agreementId = UUID.randomUUID();
        SharingAgreement draft = new SharingAgreement.Builder()
                .withId(agreementId)
                .withStatus(SharingAgreementStatus.DRAFT)
                .build();
        when(getSharingAgreementService.findById(agreementId)).thenReturn(draft);

        service().delete(plantId, agreementId);

        verify(repository).delete(plantId, agreementId);
    }
}
