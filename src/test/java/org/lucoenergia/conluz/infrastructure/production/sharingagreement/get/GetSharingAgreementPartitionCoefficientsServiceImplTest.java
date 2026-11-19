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
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anySet;
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
    @Mock
    private ZoneResolver zoneResolver;

    private static final UUID PLANT_ID = UUID.randomUUID();
    private static final Instant AGREEMENT_CREATED_AT = Instant.parse("2025-01-01T00:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    private GetSharingAgreementPartitionCoefficientsServiceImpl service() {
        return new GetSharingAgreementPartitionCoefficientsServiceImpl(
                getSharingAgreementService, getSharingAgreementRepository, getCoefficientRepository, getSupplyRepository,
                zoneResolver);
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
        when(getCoefficientRepository.findAllByPlantId(PLANT_ID)).thenReturn(List.of(
                coefficient(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null)));

        List<SharingAgreementCoefficient> result = service().findBySharingAgreementId(PLANT_ID, agreementId);

        assertTrue(result.isEmpty());
    }

    @Test
    void endStatesAreDerivedFromThePlantsAgreementsAndCoefficients() {
        UUID agreementId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        Instant handoff = Instant.parse("2025-06-01T00:00:00Z");
        SupplyPartitionCoefficient row = coefficient(UUID.randomUUID(), agreementId, supplyId,
                Instant.parse("2025-01-01T00:00:00Z"), handoff);
        SharingAgreement next = agreement(UUID.randomUUID(), PLANT_ID);
        SupplyPartitionCoefficient successor = coefficient(UUID.randomUUID(), next.getId(), supplyId, handoff, null);
        stubAgreementAndCoefficients(agreementId, supplyId, List.of(row, successor), List.of(next));

        SharingAgreementCoefficient view = onlyView(agreementId);

        assertEquals(CoefficientEndState.DERIVED, view.getEndState());
        assertEquals(handoff, view.getEndDate());
        assertEquals(CoefficientApplicationState.APPLIED, view.getApplicationState());
    }

    @Test
    void returnsOnlyTheRowsOfTheAgreementRead() {
        UUID agreementId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        SupplyPartitionCoefficient row = coefficient(UUID.randomUUID(), agreementId, supplyId,
                Instant.parse("2025-01-01T00:00:00Z"), null);
        SharingAgreement historical = agreement(UUID.randomUUID(), PLANT_ID);
        SupplyPartitionCoefficient historicalRow = coefficient(UUID.randomUUID(), historical.getId(), supplyId,
                Instant.parse("2023-01-01T00:00:00Z"), Instant.parse("2025-01-01T00:00:00Z"));
        stubAgreementAndCoefficients(agreementId, supplyId, List.of(historicalRow, row), List.of(historical));

        SharingAgreementCoefficient view = onlyView(agreementId);

        assertEquals(row.getId(), view.getCoefficientId());
        assertEquals(CoefficientEndState.OPEN, view.getEndState());
        assertNull(view.getEndDate());
    }

    @Test
    void applicationStateIsPending_whenValidFromIsNull_independentOfEndState() {
        UUID agreementId = UUID.randomUUID();
        UUID supplyId = UUID.randomUUID();
        SupplyPartitionCoefficient row = coefficient(UUID.randomUUID(), agreementId, supplyId, null, null);
        stubAgreementAndCoefficients(agreementId, supplyId, List.of(row), List.of());

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
        when(getCoefficientRepository.findAllByPlantId(PLANT_ID)).thenReturn(List.of(rowB, rowA));
        when(getSupplyRepository.findAllByIds(anySet())).thenReturn(List.of(
                supply(supplyIdB, "ES0000000000000000ZZ", "Supply B"),
                supply(supplyIdA, "ES0000000000000000AA", "Supply A")));
        when(getSharingAgreementRepository.findByPlantId(PLANT_ID, null)).thenReturn(List.of());
        when(zoneResolver.resolveApplicationZoneId()).thenReturn(ZONE);

        List<SharingAgreementCoefficient> result = service().findBySharingAgreementId(PLANT_ID, agreementId);

        assertEquals(2, result.size());
        assertEquals("ES0000000000000000AA", result.get(0).getSupplyCode());
        assertEquals("ES0000000000000000ZZ", result.get(1).getSupplyCode());
    }

    private void stubAgreementAndCoefficients(UUID agreementId, UUID supplyId,
                                              List<SupplyPartitionCoefficient> plantCoefficients,
                                              List<SharingAgreement> otherAgreements) {
        SharingAgreement agreement = agreement(agreementId, PLANT_ID);
        when(getSharingAgreementService.findById(agreementId)).thenReturn(agreement);
        when(getCoefficientRepository.findAllByPlantId(PLANT_ID)).thenReturn(plantCoefficients);
        List<SharingAgreement> plantAgreements = new ArrayList<>(otherAgreements);
        plantAgreements.add(agreement);
        when(getSharingAgreementRepository.findByPlantId(PLANT_ID, null)).thenReturn(plantAgreements);
        when(zoneResolver.resolveApplicationZoneId()).thenReturn(ZONE);
        when(getSupplyRepository.findAllByIds(Set.of(supplyId)))
                .thenReturn(List.of(supply(supplyId, "ES0000000000000000AA", "Test supply")));
    }

    private SharingAgreementCoefficient onlyView(UUID agreementId) {
        List<SharingAgreementCoefficient> result = service().findBySharingAgreementId(PLANT_ID, agreementId);
        assertEquals(1, result.size());
        return result.get(0);
    }
}
