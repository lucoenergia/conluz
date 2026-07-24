package org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement.activation;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientResolver;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.activation.CoefficientActivationErrorCode;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.activation.CoefficientActivationException;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.activation.CoefficientActivationService;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement.SharingAgreementRepository;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;

@Transactional
class CoefficientActivationServiceImplIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CoefficientActivationService service;
    @Autowired
    private CoefficientResolver coefficientResolver;
    @Autowired
    private GetSupplyPartitionCoefficientRepository getCoefficientRepository;
    @Autowired
    private ZoneResolver zoneResolver;
    @Autowired
    private SharingAgreementRepository sharingAgreementRepository;
    @Autowired
    private SaveSupplyPartitionCoefficientRepository saveCoefficientRepository;
    @Autowired
    private SupplyRepository supplyRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CommunityJpaRepository communityJpaRepository;
    @Autowired
    private PlantRepository plantRepository;

    private SupplyEntity persistSupply() {
        UserEntity user = userRepository.save(UserMother.randomUserEntity());
        return supplyRepository.save(SupplyEntityMother.random(user, communityJpaRepository.getReferenceById(DEFAULT_COMMUNITY_ID)));
    }

    private SharingAgreementEntity persistAgreement(PlantEntity plant, SharingAgreementStatus status) {
        SharingAgreementEntity agreement = new SharingAgreementEntity();
        agreement.setId(UUID.randomUUID());
        agreement.setPlant(plant);
        agreement.setName("Test agreement " + UUID.randomUUID());
        agreement.setStatus(status);
        agreement.setCreatedAt(Instant.now());
        agreement.setCreatedBy(null);
        return sharingAgreementRepository.save(agreement);
    }

    private SupplyPartitionCoefficient persistCoefficient(SupplyEntity supply, PlantEntity plant, SharingAgreementEntity agreement,
                                                            Instant validFrom, Instant validTo) {
        return saveCoefficientRepository.save(new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(supply.getId())
                .withPlantId(plant.getId())
                .withSharingAgreementId(agreement.getId())
                .withCoefficient(BigDecimal.ONE)
                .withValidFrom(validFrom)
                .withValidTo(validTo)
                .withCreatedAt(Instant.now())
                .build());
    }

    private ZoneId zone(UUID plantId) {
        return zoneResolver.resolveZoneId(plantId);
    }

    @Test
    void activationCascadeSatisfiesExclusionConstraintNoOverlapPersisted() {
        SupplyEntity supply = persistSupply();
        PlantEntity plant = plantRepository.save(PlantMother.randomPlantEntity().withSupply(supply).build());
        SharingAgreementEntity predecessorAgreement = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);
        SupplyPartitionCoefficient predecessor = persistCoefficient(supply, plant, predecessorAgreement,
                Instant.parse("2024-01-01T00:00:00Z"), null);
        SharingAgreementEntity successorAgreement = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);
        SupplyPartitionCoefficient successor = persistCoefficient(supply, plant, successorAgreement, null, null);

        LocalDate appliedOn = LocalDate.of(2025, 1, 1);
        List<SupplyPartitionCoefficient> result = service.setValidFrom(plant.getId(), successorAgreement.getId(),
                appliedOn, List.of(successor.getId()));

        Instant expected = appliedOn.atStartOfDay(zone(plant.getId())).toInstant();
        assertTrue(result.stream().anyMatch(c -> c.getId().equals(successor.getId()) && expected.equals(c.getValidFrom())));
        assertTrue(result.stream().anyMatch(c -> c.getId().equals(predecessor.getId()) && expected.equals(c.getValidTo())));
    }

    @Test
    void emptyRangeCorrectionRejectedBeforeReachingPostgresNoRowWritten() {
        SupplyEntity supply = persistSupply();
        PlantEntity plant = plantRepository.save(PlantMother.randomPlantEntity().withSupply(supply).build());
        SharingAgreementEntity predecessorAgreement = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);
        Instant predecessorValidFrom = Instant.parse("2024-01-01T00:00:00Z");
        Instant boundary = Instant.parse("2024-06-01T00:00:00Z");
        SupplyPartitionCoefficient predecessor = persistCoefficient(supply, plant, predecessorAgreement, predecessorValidFrom, boundary);
        SharingAgreementEntity successorAgreement = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);
        SupplyPartitionCoefficient successor = persistCoefficient(supply, plant, successorAgreement, boundary, null);

        // D' == predecessor.validFrom -> the empty range [f, f) Postgres would accept silently.
        LocalDate emptyRangeDate = predecessorValidFrom.atZone(zone(plant.getId())).toLocalDate();

        CoefficientActivationException exception = assertThrows(CoefficientActivationException.class,
                () -> service.setValidFrom(plant.getId(), successorAgreement.getId(), emptyRangeDate, List.of(successor.getId())));

        assertEquals(CoefficientActivationErrorCode.ACTIVATION_DATE_NOT_AFTER_PREDECESSOR, exception.getErrors().get(0).getCode());
        // Nothing was written: both rows are exactly as they were before the rejected call.
        List<SupplyPartitionCoefficient> reread = getCoefficientRepository.findAllByIdAndSharingAgreementId(
                List.of(successor.getId()), successorAgreement.getId());
        assertEquals(boundary, reread.get(0).getValidFrom());
        List<SupplyPartitionCoefficient> predecessorReread = getCoefficientRepository.findAllByIdAndSharingAgreementId(
                List.of(predecessor.getId()), predecessorAgreement.getId());
        assertEquals(boundary, predecessorReread.get(0).getValidTo());
    }

    @Test
    void reopenRejectedWhenSuccessorRowAlreadyWrittenByCascade() {
        SupplyEntity supply = persistSupply();
        PlantEntity plant = plantRepository.save(PlantMother.randomPlantEntity().withSupply(supply).build());
        SharingAgreementEntity agreementN = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);
        Instant boundary = Instant.parse("2024-06-01T00:00:00Z");
        SupplyPartitionCoefficient closedByCascade = persistCoefficient(supply, plant, agreementN,
                Instant.parse("2024-01-01T00:00:00Z"), boundary);
        SharingAgreementEntity agreementNPlus1 = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);
        persistCoefficient(supply, plant, agreementNPlus1, boundary, null); // starts exactly at boundary -- cascade-derived

        CoefficientActivationException exception = assertThrows(CoefficientActivationException.class,
                () -> service.setValidTo(plant.getId(), agreementN.getId(), null, List.of(closedByCascade.getId())));

        assertEquals(CoefficientActivationErrorCode.COEFFICIENT_HAS_SUCCESSOR, exception.getErrors().get(0).getCode());
    }

    @Test
    void closeDateCorrectedWhenSelfAuthoredAndNoSuccessorStartsThere() {
        SupplyEntity supply = persistSupply();
        PlantEntity plant = plantRepository.save(PlantMother.randomPlantEntity().withSupply(supply).build());
        SharingAgreementEntity agreement = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);
        SupplyPartitionCoefficient exitCase = persistCoefficient(supply, plant, agreement,
                Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-06-01T00:00:00Z"));

        LocalDate corrected = LocalDate.of(2024, 7, 1);
        List<SupplyPartitionCoefficient> result = service.setValidTo(plant.getId(), agreement.getId(), corrected,
                List.of(exitCase.getId()));

        assertEquals(corrected.atStartOfDay(zone(plant.getId())).toInstant(), result.get(0).getValidTo());
    }

    @Test
    void batchOfThreeAcrossThreeSuppliesWithDifferentPredecessorStates() {
        PlantEntity plant = plantRepository.save(PlantMother.randomPlantEntity().withSupply(persistSupply()).build());
        SharingAgreementEntity targetAgreement = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);

        // Supply 1: open predecessor in a different agreement.
        SupplyEntity supply1 = persistSupply();
        SharingAgreementEntity predecessorAgreement1 = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);
        SupplyPartitionCoefficient predecessor1 = persistCoefficient(supply1, plant, predecessorAgreement1,
                Instant.parse("2024-01-01T00:00:00Z"), null);
        SupplyPartitionCoefficient target1 = persistCoefficient(supply1, plant, targetAgreement, null, null);

        // Supply 2: no predecessor at all (first-ever agreement for this supply).
        SupplyEntity supply2 = persistSupply();
        SupplyPartitionCoefficient target2 = persistCoefficient(supply2, plant, targetAgreement, null, null);

        // Supply 3: predecessor already closed by an earlier chain (boundary-match, not open-row).
        SupplyEntity supply3 = persistSupply();
        SharingAgreementEntity oldestAgreement3 = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);
        Instant boundary3 = Instant.parse("2024-03-01T00:00:00Z");
        persistCoefficient(supply3, plant, oldestAgreement3, Instant.parse("2023-01-01T00:00:00Z"), boundary3);
        SharingAgreementEntity predecessorAgreement3 = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);
        SupplyPartitionCoefficient predecessor3 = persistCoefficient(supply3, plant, predecessorAgreement3, boundary3, null);
        SupplyPartitionCoefficient target3 = persistCoefficient(supply3, plant, targetAgreement, null, null);

        LocalDate appliedOn = LocalDate.of(2025, 1, 1);
        Instant expected = appliedOn.atStartOfDay(zone(plant.getId())).toInstant();

        List<SupplyPartitionCoefficient> result = service.setValidFrom(plant.getId(), targetAgreement.getId(), appliedOn,
                List.of(target1.getId(), target2.getId(), target3.getId()));

        assertTrue(result.stream().anyMatch(c -> c.getId().equals(target1.getId()) && expected.equals(c.getValidFrom())));
        assertTrue(result.stream().anyMatch(c -> c.getId().equals(predecessor1.getId()) && expected.equals(c.getValidTo())));
        assertTrue(result.stream().anyMatch(c -> c.getId().equals(target2.getId()) && expected.equals(c.getValidFrom())));
        assertEquals(5, result.size()); // target1+predecessor1 (2), target2 alone (1), target3+predecessor3 (2)
        assertTrue(result.stream().anyMatch(c -> c.getId().equals(target3.getId()) && expected.equals(c.getValidFrom())));
        assertTrue(result.stream().anyMatch(c -> c.getId().equals(predecessor3.getId()) && expected.equals(c.getValidTo())));
    }

    @Test
    void reversionWithNoPredecessorLeavesGapResolverReturnsZero() {
        SupplyEntity supply = persistSupply();
        PlantEntity plant = plantRepository.save(PlantMother.randomPlantEntity().withSupply(supply).build());
        SharingAgreementEntity agreement = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);
        Instant validFrom = Instant.parse("2024-01-01T00:00:00Z");
        SupplyPartitionCoefficient coefficient = persistCoefficient(supply, plant, agreement, validFrom, null);

        service.setValidFrom(plant.getId(), agreement.getId(), null, List.of(coefficient.getId()));

        BigDecimal resolved = coefficientResolver.resolveCoefficient(plant.getId(), supply.getId(), validFrom.plusSeconds(3600));
        assertEquals(0, BigDecimal.ZERO.compareTo(resolved));
    }

    @Test
    void resolverPicksUpActivationWithNoResolverChange() {
        SupplyEntity supply = persistSupply();
        PlantEntity plant = plantRepository.save(PlantMother.randomPlantEntity().withSupply(supply).build());
        SharingAgreementEntity oldAgreement = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);
        SupplyPartitionCoefficient oldCoefficient = persistCoefficient(supply, plant, oldAgreement,
                Instant.parse("2024-01-01T00:00:00Z"), null);
        oldCoefficient = new SupplyPartitionCoefficient.Builder()
                .withId(oldCoefficient.getId()).withSupplyId(supply.getId()).withPlantId(plant.getId())
                .withSharingAgreementId(oldAgreement.getId()).withCoefficient(BigDecimal.valueOf(0.4))
                .withValidFrom(oldCoefficient.getValidFrom()).withValidTo(null).withCreatedAt(Instant.now()).build();
        saveCoefficientRepository.save(oldCoefficient);

        SharingAgreementEntity newAgreement = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);
        SupplyPartitionCoefficient pendingNew = new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID()).withSupplyId(supply.getId()).withPlantId(plant.getId())
                .withSharingAgreementId(newAgreement.getId()).withCoefficient(BigDecimal.valueOf(0.9))
                .withValidFrom(null).withValidTo(null).withCreatedAt(Instant.now()).build();
        pendingNew = saveCoefficientRepository.save(pendingNew);

        LocalDate activationDate = LocalDate.of(2025, 1, 1);
        Instant d = activationDate.atStartOfDay(zone(plant.getId())).toInstant();
        service.setValidFrom(plant.getId(), newAgreement.getId(), activationDate, List.of(pendingNew.getId()));

        BigDecimal beforeD = coefficientResolver.resolveCoefficient(plant.getId(), supply.getId(), d.minusSeconds(3600));
        BigDecimal atAndAfterD = coefficientResolver.resolveCoefficient(plant.getId(), supply.getId(), d);

        assertEquals(0, BigDecimal.valueOf(0.4).compareTo(beforeD));
        assertEquals(0, BigDecimal.valueOf(0.9).compareTo(atAndAfterD));
    }

    @Test
    void agreementRevertsToPublishedViaSuccessorReversion() {
        SupplyEntity supply = persistSupply();
        PlantEntity plant = plantRepository.save(PlantMother.randomPlantEntity().withSupply(supply).build());
        SharingAgreementEntity agreementN = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);
        Instant boundary = Instant.parse("2024-06-01T00:00:00Z");
        persistCoefficient(supply, plant, agreementN, Instant.parse("2024-01-01T00:00:00Z"), null);
        SharingAgreementEntity agreementNPlus1 = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);
        SupplyPartitionCoefficient successorPending = persistCoefficient(supply, plant, agreementNPlus1, null, null);

        // Activating N+1's coefficient cascades: closes N's row, N becomes SUPERSEDED.
        LocalDate activatedOn = boundary.atZone(zone(plant.getId())).toLocalDate();
        List<SupplyPartitionCoefficient> activated = service.setValidFrom(plant.getId(), agreementNPlus1.getId(), activatedOn,
                List.of(successorPending.getId()));
        UUID successorId = activated.stream().filter(c -> c.getSharingAgreementId().equals(agreementNPlus1.getId()))
                .findFirst().orElseThrow().getId();
        assertEquals(SharingAgreementStatus.SUPERSEDED, sharingAgreementRepository.findById(agreementN.getId()).orElseThrow().getStatus());

        // Reverting N+1's coefficient to pending splices N's row back open -- N returns to PUBLISHED,
        // as a consequence, with no stored "previous status" involved (the recompute is purely derived).
        service.setValidFrom(plant.getId(), agreementNPlus1.getId(), null, List.of(successorId));

        assertEquals(SharingAgreementStatus.PUBLISHED, sharingAgreementRepository.findById(agreementN.getId()).orElseThrow().getStatus());
    }

    @Test
    void correctionAcceptedOnAlreadySupersededAgreement() {
        SupplyEntity supply = persistSupply();
        PlantEntity plant = plantRepository.save(PlantMother.randomPlantEntity().withSupply(supply).build());
        SharingAgreementEntity agreement = persistAgreement(plant, SharingAgreementStatus.SUPERSEDED);
        Instant originalValidFrom = Instant.parse("2024-01-01T00:00:00Z");
        Instant ownValidTo = Instant.parse("2024-06-01T00:00:00Z");
        SupplyPartitionCoefficient coefficient = persistCoefficient(supply, plant, agreement, originalValidFrom, ownValidTo);

        LocalDate corrected = LocalDate.of(2024, 2, 1);
        List<SupplyPartitionCoefficient> result = service.setValidFrom(plant.getId(), agreement.getId(), corrected,
                List.of(coefficient.getId()));

        assertEquals(corrected.atStartOfDay(zone(plant.getId())).toInstant(), result.get(0).getValidFrom());
    }

    @Test
    void setValidFromBatchIsIdempotentOnResend() {
        SupplyEntity supply = persistSupply();
        PlantEntity plant = plantRepository.save(PlantMother.randomPlantEntity().withSupply(supply).build());
        SharingAgreementEntity agreement = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);
        SupplyPartitionCoefficient pending = persistCoefficient(supply, plant, agreement, null, null);

        LocalDate appliedOn = LocalDate.of(2025, 1, 1);
        service.setValidFrom(plant.getId(), agreement.getId(), appliedOn, List.of(pending.getId()));
        List<SupplyPartitionCoefficient> secondCall = service.setValidFrom(plant.getId(), agreement.getId(), appliedOn,
                List.of(pending.getId()));

        assertTrue(secondCall.isEmpty()); // no-op: nothing changed, nothing re-written
    }

    @Test
    void setValidToBatchIsIdempotentOnResend() {
        SupplyEntity supply = persistSupply();
        PlantEntity plant = plantRepository.save(PlantMother.randomPlantEntity().withSupply(supply).build());
        SharingAgreementEntity agreement = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);
        SupplyPartitionCoefficient open = persistCoefficient(supply, plant, agreement, Instant.parse("2024-01-01T00:00:00Z"), null);

        LocalDate closedOn = LocalDate.of(2024, 6, 1);
        service.setValidTo(plant.getId(), agreement.getId(), closedOn, List.of(open.getId()));
        List<SupplyPartitionCoefficient> secondCall = service.setValidTo(plant.getId(), agreement.getId(), closedOn,
                List.of(open.getId()));

        assertTrue(secondCall.isEmpty());
    }
}
