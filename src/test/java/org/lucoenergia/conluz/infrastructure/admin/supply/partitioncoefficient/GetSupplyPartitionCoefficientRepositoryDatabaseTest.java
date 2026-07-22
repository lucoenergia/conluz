package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
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
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;

@Transactional
class GetSupplyPartitionCoefficientRepositoryDatabaseTest extends BaseIntegrationTest {

    @Autowired
    private GetSupplyPartitionCoefficientRepositoryDatabase repository;

    @Autowired
    private SaveSupplyPartitionCoefficientRepository saveRepository;

    @Autowired
    private SupplyRepository supplyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommunityJpaRepository communityJpaRepository;

    @Autowired
    private PlantRepository plantRepository;

    @Autowired
    private SharingAgreementRepository sharingAgreementRepository;

    private SupplyEntity persistSupply() {
        UserEntity user = UserMother.randomUserEntity();
        userRepository.save(user);
        return supplyRepository.save(SupplyEntityMother.random(
                user,
                communityJpaRepository.getReferenceById(DEFAULT_COMMUNITY_ID)
        ));
    }

    private SharingAgreementEntity persistPlantAndPublishedAgreement(SupplyEntity supply) {
        PlantEntity plant = plantRepository.save(PlantMother.randomPlantEntity().withSupply(supply).build());
        SharingAgreementEntity agreement = new SharingAgreementEntity();
        agreement.setId(UUID.randomUUID());
        agreement.setPlant(plant);
        agreement.setName("Test agreement " + UUID.randomUUID());
        agreement.setStatus(SharingAgreementStatus.PUBLISHED);
        agreement.setCreatedAt(Instant.now());
        agreement.setCreatedBy(null);
        return sharingAgreementRepository.save(agreement);
    }

    private SupplyPartitionCoefficient persist(UUID supplyId, UUID plantId, UUID sharingAgreementId,
                                               BigDecimal coefficient, Instant validFrom, Instant validTo) {
        return saveRepository.save(new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(supplyId)
                .withPlantId(plantId)
                .withSharingAgreementId(sharingAgreementId)
                .withCoefficient(coefficient)
                .withValidFrom(validFrom)
                .withValidTo(validTo)
                .withCreatedAt(Instant.now())
                .build());
    }

    @Test
    void findActiveBySupplyIdReturnsRowWithNullValidTo() {
        SupplyEntity supply = persistSupply();
        SharingAgreementEntity agreement = persistPlantAndPublishedAgreement(supply);
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2025-01-01T00:00:00Z");
        persist(supply.getId(), agreement.getPlant().getId(), agreement.getId(), BigDecimal.valueOf(1.000000), t0, t1);
        persist(supply.getId(), agreement.getPlant().getId(), agreement.getId(), BigDecimal.valueOf(2.000000), t1, null);

        Optional<SupplyPartitionCoefficient> result = repository.findActiveBySupplyId(supply.getId());

        assertTrue(result.isPresent());
        assertEquals(0, BigDecimal.valueOf(2.000000).compareTo(result.get().getCoefficient()));
        assertNull(result.get().getValidTo());
    }

    @Test
    void findBySupplyIdAtTimestampRespectsInclusiveLowerBound() {
        SupplyEntity supply = persistSupply();
        SharingAgreementEntity agreement = persistPlantAndPublishedAgreement(supply);
        Instant changeAt = Instant.parse("2025-03-01T00:00:00Z");
        persist(supply.getId(), agreement.getPlant().getId(), agreement.getId(),
                BigDecimal.valueOf(3.000000), Instant.parse("2024-01-01T00:00:00Z"), changeAt);
        persist(supply.getId(), agreement.getPlant().getId(), agreement.getId(),
                BigDecimal.valueOf(4.000000), changeAt, null);

        // Query at the exact change time should return the new period (valid_from inclusive)
        Optional<SupplyPartitionCoefficient> result = repository.findBySupplyIdAtTimestamp(supply.getId(), changeAt);

        assertTrue(result.isPresent());
        assertEquals(0, BigDecimal.valueOf(4.000000).compareTo(result.get().getCoefficient()));
    }

    @Test
    void findBySupplyIdInRangeReturnsOverlappingPeriods() {
        SupplyEntity supply = persistSupply();
        SharingAgreementEntity agreement = persistPlantAndPublishedAgreement(supply);
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2025-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2025-06-01T00:00:00Z");
        persist(supply.getId(), agreement.getPlant().getId(), agreement.getId(), BigDecimal.valueOf(1.000000), t0, t1);
        persist(supply.getId(), agreement.getPlant().getId(), agreement.getId(), BigDecimal.valueOf(2.000000), t1, null);

        List<SupplyPartitionCoefficient> result = repository.findBySupplyIdInRange(
                supply.getId(), Instant.parse("2024-06-01T00:00:00Z"), t2);

        assertEquals(2, result.size());
    }

    @Test
    void findAllActiveAtTimestampReturnsOnlyActiveRows() {
        SupplyEntity supply1 = persistSupply();
        SharingAgreementEntity agreement1 = persistPlantAndPublishedAgreement(supply1);
        SupplyEntity supply2 = persistSupply();
        SharingAgreementEntity agreement2 = persistPlantAndPublishedAgreement(supply2);
        SupplyEntity supply3 = persistSupply();
        SharingAgreementEntity agreement3 = persistPlantAndPublishedAgreement(supply3);

        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2025-01-01T00:00:00Z");
        Instant queryAt = Instant.parse("2025-06-01T00:00:00Z");

        persist(supply1.getId(), agreement1.getPlant().getId(), agreement1.getId(), BigDecimal.valueOf(10.0), t0, null);
        persist(supply2.getId(), agreement2.getPlant().getId(), agreement2.getId(), BigDecimal.valueOf(20.0), t0, null);
        // closed row — should not appear
        persist(supply3.getId(), agreement3.getPlant().getId(), agreement3.getId(), BigDecimal.valueOf(30.0), t0, t1);

        List<SupplyPartitionCoefficient> result = repository.findAllActiveAtTimestamp(queryAt);

        assertTrue(result.stream().anyMatch(c -> c.getSupplyId().equals(supply1.getId())));
        assertTrue(result.stream().anyMatch(c -> c.getSupplyId().equals(supply2.getId())));
        assertTrue(result.stream().noneMatch(c -> c.getValidTo() != null && c.getValidTo().isBefore(queryAt)));
    }

    @Test
    void findAllBySupplyIdOrderByValidFromAscReturnsChronologicalHistory() {
        SupplyEntity supply = persistSupply();
        SharingAgreementEntity agreement = persistPlantAndPublishedAgreement(supply);
        Instant t0 = Instant.parse("2023-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2025-01-01T00:00:00Z");
        persist(supply.getId(), agreement.getPlant().getId(), agreement.getId(), BigDecimal.valueOf(1.000000), t0, t1);
        persist(supply.getId(), agreement.getPlant().getId(), agreement.getId(), BigDecimal.valueOf(2.000000), t1, t2);
        persist(supply.getId(), agreement.getPlant().getId(), agreement.getId(), BigDecimal.valueOf(3.000000), t2, null);

        List<SupplyPartitionCoefficient> history = repository.findAllBySupplyIdOrderByValidFromAsc(supply.getId());

        assertEquals(3, history.size());
        assertEquals(t0, history.get(0).getValidFrom());
        assertEquals(t1, history.get(1).getValidFrom());
        assertEquals(t2, history.get(2).getValidFrom());
    }

    // --- Plant-scoped lookups (CoefficientResolver support) ---

    @Test
    void findByPlantIdAndSupplyIdAtTimestampDisambiguatesConcurrentPlants() {
        SupplyEntity supply = persistSupply();
        SharingAgreementEntity agreement1 = persistPlantAndPublishedAgreement(supply);
        SharingAgreementEntity agreement2 = persistPlantAndPublishedAgreement(supply);
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        Instant queryAt = Instant.parse("2024-06-01T00:00:00Z");
        // Same supply, concurrently active on two different plants -- only the exclusion constraint's
        // (plant_id, supply_id) scoping (not supply_id alone) allows this.
        persist(supply.getId(), agreement1.getPlant().getId(), agreement1.getId(), BigDecimal.valueOf(0.4), t0, null);
        persist(supply.getId(), agreement2.getPlant().getId(), agreement2.getId(), BigDecimal.valueOf(0.6), t0, null);

        Optional<SupplyPartitionCoefficient> onPlant1 = repository.findByPlantIdAndSupplyIdAtTimestamp(
                agreement1.getPlant().getId(), supply.getId(), queryAt);
        Optional<SupplyPartitionCoefficient> onPlant2 = repository.findByPlantIdAndSupplyIdAtTimestamp(
                agreement2.getPlant().getId(), supply.getId(), queryAt);

        assertTrue(onPlant1.isPresent());
        assertEquals(0, BigDecimal.valueOf(0.4).compareTo(onPlant1.get().getCoefficient()));
        assertTrue(onPlant2.isPresent());
        assertEquals(0, BigDecimal.valueOf(0.6).compareTo(onPlant2.get().getCoefficient()));
    }

    @Test
    void findByPlantIdAndSupplyIdAtTimestampRespectsHalfOpenBoundary() {
        SupplyEntity supply = persistSupply();
        SharingAgreementEntity agreement = persistPlantAndPublishedAgreement(supply);
        Instant changeAt = Instant.parse("2025-03-01T00:00:00Z");
        persist(supply.getId(), agreement.getPlant().getId(), agreement.getId(),
                BigDecimal.valueOf(1.0), Instant.parse("2024-01-01T00:00:00Z"), changeAt);
        persist(supply.getId(), agreement.getPlant().getId(), agreement.getId(),
                BigDecimal.valueOf(2.0), changeAt, null);

        Optional<SupplyPartitionCoefficient> atChange = repository.findByPlantIdAndSupplyIdAtTimestamp(
                agreement.getPlant().getId(), supply.getId(), changeAt);

        assertTrue(atChange.isPresent());
        assertEquals(0, BigDecimal.valueOf(2.0).compareTo(atChange.get().getCoefficient()));
    }

    @Test
    void findAllBySupplyIdAtTimestampReturnsEveryPlantActiveForThatSupply() {
        SupplyEntity supply = persistSupply();
        SharingAgreementEntity agreement1 = persistPlantAndPublishedAgreement(supply);
        SharingAgreementEntity agreement2 = persistPlantAndPublishedAgreement(supply);
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        Instant queryAt = Instant.parse("2024-06-01T00:00:00Z");
        persist(supply.getId(), agreement1.getPlant().getId(), agreement1.getId(), BigDecimal.valueOf(0.4), t0, null);
        persist(supply.getId(), agreement2.getPlant().getId(), agreement2.getId(), BigDecimal.valueOf(0.6), t0, null);

        List<SupplyPartitionCoefficient> result = repository.findAllBySupplyIdAtTimestamp(supply.getId(), queryAt);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(c -> c.getPlantId().equals(agreement1.getPlant().getId())));
        assertTrue(result.stream().anyMatch(c -> c.getPlantId().equals(agreement2.getPlant().getId())));
    }

    @Test
    void findAllBySupplyIdAtTimestampExcludesClosedPeriods() {
        SupplyEntity supply = persistSupply();
        SharingAgreementEntity agreement = persistPlantAndPublishedAgreement(supply);
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2024-06-01T00:00:00Z");
        Instant queryAt = Instant.parse("2025-01-01T00:00:00Z");
        persist(supply.getId(), agreement.getPlant().getId(), agreement.getId(), BigDecimal.valueOf(0.4), t0, t1);

        List<SupplyPartitionCoefficient> result = repository.findAllBySupplyIdAtTimestamp(supply.getId(), queryAt);

        assertTrue(result.isEmpty());
    }
}
