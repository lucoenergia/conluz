package org.lucoenergia.conluz.infrastructure.production.plant.publish;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.production.plant.get.GetSharingAgreementService;
import org.lucoenergia.conluz.domain.production.plant.publish.PublishSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementHasNoCoefficientsException;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementNotDraftException;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementStatus;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublishSharingAgreementServiceImplTest {

    @Mock
    private GetSharingAgreementService getSharingAgreementService;
    @Mock
    private GetSupplyPartitionCoefficientRepository supplyPartitionCoefficientRepository;
    @Mock
    private PublishSharingAgreementRepository repository;

    private PublishSharingAgreementServiceImpl service() {
        return new PublishSharingAgreementServiceImpl(getSharingAgreementService, supplyPartitionCoefficientRepository, repository);
    }

    @Test
    void publish_throwsNotDraft_whenAgreementIsPublished() {
        UUID agreementId = UUID.randomUUID();
        SharingAgreement published = new SharingAgreement.Builder()
                .withId(agreementId)
                .withStatus(SharingAgreementStatus.PUBLISHED)
                .build();
        when(getSharingAgreementService.findById(agreementId)).thenReturn(published);

        assertThrows(SharingAgreementNotDraftException.class, () -> service().publish(UUID.randomUUID(), agreementId));
        verify(supplyPartitionCoefficientRepository, never()).existsBySharingAgreementId(agreementId);
    }

    @Test
    void publish_throwsHasNoCoefficients_whenNoCoefficientsExist() {
        UUID agreementId = UUID.randomUUID();
        SharingAgreement draft = new SharingAgreement.Builder()
                .withId(agreementId)
                .withStatus(SharingAgreementStatus.DRAFT)
                .build();
        when(getSharingAgreementService.findById(agreementId)).thenReturn(draft);
        when(supplyPartitionCoefficientRepository.existsBySharingAgreementId(agreementId)).thenReturn(false);

        assertThrows(SharingAgreementHasNoCoefficientsException.class,
                () -> service().publish(UUID.randomUUID(), agreementId));
        verify(repository, never()).publish(any(), any());
    }

    @Test
    void publish_delegatesToRepository_whenDraftWithCoefficients() {
        UUID plantId = UUID.randomUUID();
        UUID agreementId = UUID.randomUUID();
        SharingAgreement draft = new SharingAgreement.Builder()
                .withId(agreementId)
                .withStatus(SharingAgreementStatus.DRAFT)
                .build();
        when(getSharingAgreementService.findById(agreementId)).thenReturn(draft);
        when(supplyPartitionCoefficientRepository.existsBySharingAgreementId(agreementId)).thenReturn(true);
        SharingAgreement published = new SharingAgreement.Builder()
                .withId(agreementId)
                .withStatus(SharingAgreementStatus.PUBLISHED)
                .build();
        when(repository.publish(plantId, agreementId)).thenReturn(published);

        SharingAgreement result = service().publish(plantId, agreementId);

        assertEquals(SharingAgreementStatus.PUBLISHED, result.getStatus());
        verify(repository).publish(plantId, agreementId);
    }
}
