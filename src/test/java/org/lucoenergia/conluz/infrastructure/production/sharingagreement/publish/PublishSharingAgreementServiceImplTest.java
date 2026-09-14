package org.lucoenergia.conluz.infrastructure.production.sharingagreement.publish;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementService;
import org.lucoenergia.conluz.domain.production.sharingagreement.publish.PublishSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementCoefficientSumInvalidException;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementHasNoCoefficientsException;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementNotDraftException;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.publish.PublishSharingAgreementServiceImpl;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
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

    private SupplyPartitionCoefficient coefficient(BigDecimal value) {
        return new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(UUID.randomUUID())
                .withCoefficient(value)
                .build();
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
        verify(supplyPartitionCoefficientRepository, never()).findAllBySharingAgreementId(agreementId);
    }

    @Test
    void publish_throwsHasNoCoefficients_whenNoCoefficientsExist() {
        UUID agreementId = UUID.randomUUID();
        SharingAgreement draft = new SharingAgreement.Builder()
                .withId(agreementId)
                .withStatus(SharingAgreementStatus.DRAFT)
                .build();
        when(getSharingAgreementService.findById(agreementId)).thenReturn(draft);
        when(supplyPartitionCoefficientRepository.findAllBySharingAgreementId(agreementId)).thenReturn(List.of());

        assertThrows(SharingAgreementHasNoCoefficientsException.class,
                () -> service().publish(UUID.randomUUID(), agreementId));
        verify(repository, never()).publish(any(), any());
    }

    @Test
    void publish_throwsSumInvalid_whenCoefficientsDoNotSumToOne() {
        UUID agreementId = UUID.randomUUID();
        SharingAgreement draft = new SharingAgreement.Builder()
                .withId(agreementId)
                .withStatus(SharingAgreementStatus.DRAFT)
                .build();
        when(getSharingAgreementService.findById(agreementId)).thenReturn(draft);
        List<SupplyPartitionCoefficient> coefficients = List.of(
                coefficient(new BigDecimal("0.500000")),
                coefficient(new BigDecimal("0.400000")));
        when(supplyPartitionCoefficientRepository.findAllBySharingAgreementId(agreementId)).thenReturn(coefficients);

        SharingAgreementCoefficientSumInvalidException e = assertThrows(SharingAgreementCoefficientSumInvalidException.class,
                () -> service().publish(UUID.randomUUID(), agreementId));

        assertEquals(0, new BigDecimal("0.900000").compareTo(e.getActualSum()));
        verify(repository, never()).publish(any(), any());
    }

    @Test
    void publish_delegatesToRepository_whenDraftWithCoefficientsSummingToOne() {
        UUID plantId = UUID.randomUUID();
        UUID agreementId = UUID.randomUUID();
        SharingAgreement draft = new SharingAgreement.Builder()
                .withId(agreementId)
                .withStatus(SharingAgreementStatus.DRAFT)
                .build();
        when(getSharingAgreementService.findById(agreementId)).thenReturn(draft);
        List<SupplyPartitionCoefficient> coefficients = List.of(
                coefficient(new BigDecimal("0.333333")),
                coefficient(new BigDecimal("0.333333")),
                coefficient(new BigDecimal("0.333334")));
        when(supplyPartitionCoefficientRepository.findAllBySharingAgreementId(agreementId)).thenReturn(coefficients);
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
