package org.lucoenergia.conluz.infrastructure.production.sharingagreement.revert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementHasAppliedCoefficientsException;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementNotRevertibleException;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementService;
import org.lucoenergia.conluz.domain.production.sharingagreement.revert.RevertSharingAgreementToDraftRepository;
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
class RevertSharingAgreementToDraftServiceImplTest {

    @Mock
    private GetSharingAgreementService getSharingAgreementService;
    @Mock
    private GetSupplyPartitionCoefficientRepository supplyPartitionCoefficientRepository;
    @Mock
    private RevertSharingAgreementToDraftRepository repository;

    private RevertSharingAgreementToDraftServiceImpl service() {
        return new RevertSharingAgreementToDraftServiceImpl(getSharingAgreementService, supplyPartitionCoefficientRepository, repository);
    }

    @Test
    void revertToDraft_throwsNotRevertible_whenAgreementIsDraft() {
        UUID agreementId = UUID.randomUUID();
        SharingAgreement draft = new SharingAgreement.Builder()
                .withId(agreementId)
                .withStatus(SharingAgreementStatus.DRAFT)
                .build();
        when(getSharingAgreementService.findById(agreementId)).thenReturn(draft);

        assertThrows(SharingAgreementNotRevertibleException.class,
                () -> service().revertToDraft(UUID.randomUUID(), agreementId));
        verify(supplyPartitionCoefficientRepository, never())
                .existsBySharingAgreementIdAndValidFromIsNotNull(agreementId);
    }

    @Test
    void revertToDraft_throwsNotRevertible_whenAgreementIsSuperseded() {
        UUID agreementId = UUID.randomUUID();
        SharingAgreement superseded = new SharingAgreement.Builder()
                .withId(agreementId)
                .withStatus(SharingAgreementStatus.SUPERSEDED)
                .build();
        when(getSharingAgreementService.findById(agreementId)).thenReturn(superseded);

        assertThrows(SharingAgreementNotRevertibleException.class,
                () -> service().revertToDraft(UUID.randomUUID(), agreementId));
    }

    @Test
    void revertToDraft_throwsHasAppliedCoefficients_whenAnyCoefficientIsApplied() {
        UUID agreementId = UUID.randomUUID();
        SharingAgreement published = new SharingAgreement.Builder()
                .withId(agreementId)
                .withStatus(SharingAgreementStatus.PUBLISHED)
                .build();
        when(getSharingAgreementService.findById(agreementId)).thenReturn(published);
        when(supplyPartitionCoefficientRepository.existsBySharingAgreementIdAndValidFromIsNotNull(agreementId))
                .thenReturn(true);

        assertThrows(SharingAgreementHasAppliedCoefficientsException.class,
                () -> service().revertToDraft(UUID.randomUUID(), agreementId));
        verify(repository, never()).revertToDraft(any(), any());
    }

    @Test
    void revertToDraft_delegatesToRepository_whenPublishedAndInert() {
        UUID plantId = UUID.randomUUID();
        UUID agreementId = UUID.randomUUID();
        SharingAgreement published = new SharingAgreement.Builder()
                .withId(agreementId)
                .withStatus(SharingAgreementStatus.PUBLISHED)
                .build();
        when(getSharingAgreementService.findById(agreementId)).thenReturn(published);
        when(supplyPartitionCoefficientRepository.existsBySharingAgreementIdAndValidFromIsNotNull(agreementId))
                .thenReturn(false);
        SharingAgreement draft = new SharingAgreement.Builder()
                .withId(agreementId)
                .withStatus(SharingAgreementStatus.DRAFT)
                .build();
        when(repository.revertToDraft(plantId, agreementId)).thenReturn(draft);

        SharingAgreement result = service().revertToDraft(plantId, agreementId);

        assertEquals(SharingAgreementStatus.DRAFT, result.getStatus());
        verify(repository).revertToDraft(plantId, agreementId);
    }
}
