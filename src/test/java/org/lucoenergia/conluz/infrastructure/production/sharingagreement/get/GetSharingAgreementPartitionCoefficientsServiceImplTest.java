package org.lucoenergia.conluz.infrastructure.production.sharingagreement.get;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientApplicationState;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientEndState;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementNotFoundException;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementService;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.SharingAgreementCoefficient;
import org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile.SharingAgreementPlantMismatchException;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSharingAgreementPartitionCoefficientsServiceImplTest {

    @Mock
    private GetSharingAgreementService getSharingAgreementService;
    @Mock
    private GetSharingAgreementRepository getSharingAgreementRepository;
    @Mock
    private GetSupplyPartitionCoefficientRepository getCoefficientRepository;
    @Mock
    private GetSupplyRepository getSupplyRepository;

    private static final UUID PLANT_ID = UUID.randomUUID();
    private static final Instant AGREEMENT_CREATED_AT = Instant.parse("2025-01-01T00:00:00Z");

    private GetSharingAgreementPartitionCoefficientsServiceImpl service() {
        return new GetSharingAgreementPartitionCoefficientsServiceImpl(
                getSharingAgreementService, getSharingAgreementRepository, getCoefficientRepository, getSupplyRepository);
    }

    private SharingAgreement agreement(UUID id, UUID plantId) {
        return new SharingAgreement.Builder()
                .withId(id).withPlantId(plantId).withStatus(SharingAgreementStatus.PUBLISHED)
                .withCreatedAt(AGREEMENT_CREATED_AT).build();
    }

    private SupplyPartitionCoefficient coefficient(UUID id, UUID agreementId, UUID supplyId,
                                                     Instant validFrom, Instant validTo) {
        return new SupplyPartitionCoefficient.Builder()
                .withId(id).withSupplyId(supplyId).withPlantId(PLANT_ID).withSharingAgreementId(agreementId)
                .withCoefficient(BigDecimal.valueOf(0.5)).withValidFrom(validFrom).withValidTo(validTo)
                .withCreatedAt(Instant.EPOCH).build();
    }

    private Supply supply(UUID id, String code, String name) {
        return new Supply.Builder().withId(id).withCode(code).withName(name).build();
    }

    @Test
    void throwsNotFound_whenAgreementDoesNotExist() {
        UUID agreementId = UUID.randomUUID();
        when(getSharingAgreementService.findById(agreementId)).thenThrow(new SharingAgreementNotFoundException(agreementId));

        assertThrows(SharingAgreementNotFoundException.class, () -> service().findBySharingAgreementId(PLANT_ID, agreementId));
    }

    @Test
    void throwsPlantMismatch_whenAgreementBelongsToAnotherPlant() {
        UUID agreementId = UUID.randomUUID();
        when(getSharingAgreementService.findById(agreementId)).thenReturn(agreement(agreementId, UUID.randomUUID()));

        assertThrows(SharingAgreementPlantMismatchException.class,
                () -> service().findBySharingAgreementId(PLANT_ID, agreementId));
    }

    @Test
    void returnsEmptyList_whenAgreementHasNoCoefficients() {
        UUID agreementId = UUID.randomUUID();
        when(getSharingAgreementService.findById(agreementId)).thenReturn(agreement(agreementId, PLANT_ID));
        when(getCoefficientRepository.findAllBySharingAgreementId(agreementId)).thenReturn(List.of());

        List<SharingAgreementCoefficient> result = service().findBySharingAgreementId(PLANT_ID, agreementId);

        assertTrue(result.isEmpty());
    }

    @Test
    void closedState_whenValidToIsAuthoredNotCascadeDerived() {
        UUID agreementId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        Instant validTo = Instant.parse("2025-06-01T00:00:00Z");
        SupplyPartitionCoefficient row = coefficient(UUID.randomUUID(), agreementId, supplyId,
                Instant.parse("2025-01-01T00:00:00Z"), validTo);
        stubAgreementAndCoefficients(agreementId, supplyId, row);
        when(getCoefficientRepository.findNextActivatedAfter(PLANT_ID, supplyId, row.getId(), row.getValidFrom()))
                .thenReturn(Optional.empty());

        SharingAgreementCoefficient view = onlyView(agreementId);

        assertEquals(CoefficientEndState.CLOSED, view.getEndState());
        assertEquals(validTo, view.getEndDate());
        assertEquals(CoefficientApplicationState.APPLIED, view.getApplicationState());
    }

    @Test
    void derivedState_whenValidToIsCascadeDerived() {
        UUID agreementId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        Instant validTo = Instant.parse("2025-06-01T00:00:00Z");
        SupplyPartitionCoefficient row = coefficient(UUID.randomUUID(), agreementId, supplyId,
                Instant.parse("2025-01-01T00:00:00Z"), validTo);
        SupplyPartitionCoefficient successor = coefficient(UUID.randomUUID(), UUID.randomUUID(), supplyId, validTo, null);
        stubAgreementAndCoefficients(agreementId, supplyId, row);
        when(getCoefficientRepository.findNextActivatedAfter(PLANT_ID, supplyId, row.getId(), row.getValidFrom()))
                .thenReturn(Optional.of(successor));

        SharingAgreementCoefficient view = onlyView(agreementId);

        assertEquals(CoefficientEndState.DERIVED, view.getEndState());
        assertEquals(validTo, view.getEndDate());
    }

    @Test
    void derivedState_whenValidToIsNullAndNextCoefficientIsActivated() {
        UUID agreementId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        Instant successorValidFrom = Instant.parse("2025-06-01T00:00:00Z");
        SupplyPartitionCoefficient row = coefficient(UUID.randomUUID(), agreementId, supplyId,
                Instant.parse("2025-01-01T00:00:00Z"), null);
        SupplyPartitionCoefficient successor = coefficient(UUID.randomUUID(), UUID.randomUUID(), supplyId,
                successorValidFrom, null);
        stubAgreementAndCoefficients(agreementId, supplyId, row);
        when(getSharingAgreementRepository.existsLaterNonDraftAgreement(PLANT_ID, agreementId, AGREEMENT_CREATED_AT))
                .thenReturn(true);
        when(getCoefficientRepository.findNextCoefficientForSupplyInLaterAgreement(
                PLANT_ID, supplyId, agreementId, AGREEMENT_CREATED_AT)).thenReturn(Optional.of(successor));

        SharingAgreementCoefficient view = onlyView(agreementId);

        assertEquals(CoefficientEndState.DERIVED, view.getEndState());
        assertEquals(successorValidFrom, view.getEndDate());
    }

    @Test
    void pendingSuccessionState_whenValidToIsNullAndNextCoefficientIsPending() {
        UUID agreementId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        SupplyPartitionCoefficient row = coefficient(UUID.randomUUID(), agreementId, supplyId,
                Instant.parse("2025-01-01T00:00:00Z"), null);
        SupplyPartitionCoefficient pendingSuccessor = coefficient(UUID.randomUUID(), UUID.randomUUID(), supplyId, null, null);
        stubAgreementAndCoefficients(agreementId, supplyId, row);
        when(getSharingAgreementRepository.existsLaterNonDraftAgreement(PLANT_ID, agreementId, AGREEMENT_CREATED_AT))
                .thenReturn(true);
        when(getCoefficientRepository.findNextCoefficientForSupplyInLaterAgreement(
                PLANT_ID, supplyId, agreementId, AGREEMENT_CREATED_AT)).thenReturn(Optional.of(pendingSuccessor));

        SharingAgreementCoefficient view = onlyView(agreementId);

        assertEquals(CoefficientEndState.PENDING_SUCCESSION, view.getEndState());
        assertNull(view.getEndDate());
    }

    @Test
    void openOrphanState_whenValidToIsNullNoNextCoefficientButLaterAgreementExists() {
        UUID agreementId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        SupplyPartitionCoefficient row = coefficient(UUID.randomUUID(), agreementId, supplyId,
                Instant.parse("2025-01-01T00:00:00Z"), null);
        stubAgreementAndCoefficients(agreementId, supplyId, row);
        when(getSharingAgreementRepository.existsLaterNonDraftAgreement(PLANT_ID, agreementId, AGREEMENT_CREATED_AT))
                .thenReturn(true);
        when(getCoefficientRepository.findNextCoefficientForSupplyInLaterAgreement(
                PLANT_ID, supplyId, agreementId, AGREEMENT_CREATED_AT)).thenReturn(Optional.empty());

        SharingAgreementCoefficient view = onlyView(agreementId);

        assertEquals(CoefficientEndState.OPEN_ORPHAN, view.getEndState());
        assertNull(view.getEndDate());
    }

    @Test
    void openState_whenValidToIsNullAndNoLaterAgreementExists_skipsPerSupplyLookup() {
        UUID agreementId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        SupplyPartitionCoefficient row = coefficient(UUID.randomUUID(), agreementId, supplyId,
                Instant.parse("2025-01-01T00:00:00Z"), null);
        stubAgreementAndCoefficients(agreementId, supplyId, row);
        when(getSharingAgreementRepository.existsLaterNonDraftAgreement(PLANT_ID, agreementId, AGREEMENT_CREATED_AT))
                .thenReturn(false);

        SharingAgreementCoefficient view = onlyView(agreementId);

        assertEquals(CoefficientEndState.OPEN, view.getEndState());
        assertNull(view.getEndDate());
        // The short-circuit means the per-supply lookup must never even be attempted.
        org.mockito.Mockito.verify(getCoefficientRepository, org.mockito.Mockito.never())
                .findNextCoefficientForSupplyInLaterAgreement(any(), any(), any(), any());
    }

    @Test
    void applicationStateIsPending_whenValidFromIsNull_independentOfEndState() {
        UUID agreementId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        SupplyPartitionCoefficient row = coefficient(UUID.randomUUID(), agreementId, supplyId, null, null);
        stubAgreementAndCoefficients(agreementId, supplyId, row);
        when(getSharingAgreementRepository.existsLaterNonDraftAgreement(PLANT_ID, agreementId, AGREEMENT_CREATED_AT))
                .thenReturn(false);

        SharingAgreementCoefficient view = onlyView(agreementId);

        assertEquals(CoefficientApplicationState.PENDING, view.getApplicationState());
        assertEquals(CoefficientEndState.OPEN, view.getEndState());
    }

    @Test
    void resultIsSortedByCupsAscending_regardlessOfInputOrder() {
        UUID agreementId = UUID.randomUUID();
        UUID supplyIdB = UUID.randomUUID();
        UUID supplyIdA = UUID.randomUUID();
        SupplyPartitionCoefficient rowB = coefficient(UUID.randomUUID(), agreementId, supplyIdB, null, null);
        SupplyPartitionCoefficient rowA = coefficient(UUID.randomUUID(), agreementId, supplyIdA, null, null);
        when(getSharingAgreementService.findById(agreementId)).thenReturn(agreement(agreementId, PLANT_ID));
        when(getCoefficientRepository.findAllBySharingAgreementId(agreementId)).thenReturn(List.of(rowB, rowA));
        when(getSupplyRepository.findAllByIds(anySet())).thenReturn(List.of(
                supply(supplyIdB, "ES0000000000000000ZZ", "Supply B"),
                supply(supplyIdA, "ES0000000000000000AA", "Supply A")));
        when(getSharingAgreementRepository.existsLaterNonDraftAgreement(eq(PLANT_ID), eq(agreementId), any()))
                .thenReturn(false);

        List<SharingAgreementCoefficient> result = service().findBySharingAgreementId(PLANT_ID, agreementId);

        assertEquals(2, result.size());
        assertEquals("ES0000000000000000AA", result.get(0).getSupplyCode());
        assertEquals("ES0000000000000000ZZ", result.get(1).getSupplyCode());
    }

    private void stubAgreementAndCoefficients(UUID agreementId, UUID supplyId, SupplyPartitionCoefficient row) {
        when(getSharingAgreementService.findById(agreementId)).thenReturn(agreement(agreementId, PLANT_ID));
        when(getCoefficientRepository.findAllBySharingAgreementId(agreementId)).thenReturn(List.of(row));
        when(getSupplyRepository.findAllByIds(Set.of(supplyId)))
                .thenReturn(List.of(supply(supplyId, "ES0000000000000000AA", "Test supply")));
    }

    private SharingAgreementCoefficient onlyView(UUID agreementId) {
        List<SharingAgreementCoefficient> result = service().findBySharingAgreementId(PLANT_ID, agreementId);
        assertEquals(1, result.size());
        return result.get(0);
    }
}
