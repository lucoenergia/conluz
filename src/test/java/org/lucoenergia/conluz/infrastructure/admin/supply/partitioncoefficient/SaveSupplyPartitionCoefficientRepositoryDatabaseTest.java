package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionCoefficientJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement.SharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;

@Transactional
class SaveSupplyPartitionCoefficientRepositoryDatabaseTest extends BaseIntegrationTest {

    @Autowired
    private SaveSupplyPartitionCoefficientRepositoryDatabase repository;

    @Autowired
    private GetSupplyPartitionCoefficientRepository getRepository;

    @Autowired
    private SupplyPartitionCoefficientJpaRepository jpaRepository;

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        return repository.save(new SupplyPartitionCoefficient.Builder()
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
    void closeActivePeriodSetsValidToOnOpenRow() {
        SupplyEntity supply = persistSupply();
        SharingAgreementEntity agreement = persistPlantAndPublishedAgreement(supply);
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        persist(supply.getId(), agreement.getPlant().getId(), agreement.getId(), BigDecimal.valueOf(5.000000), start, null);

        Instant closeAt = Instant.parse("2025-01-01T00:00:00Z");
        repository.closeActivePeriod(supply.getId(), agreement.getPlant().getId(), closeAt);

        Optional<SupplyPartitionCoefficient> active = getRepository.findActiveBySupplyId(supply.getId());
        assertFalse(active.isPresent());

        List<SupplyPartitionCoefficient> history = getRepository.findAllBySupplyIdOrderByValidFromAsc(supply.getId());
        assertEquals(1, history.size());
        assertEquals(closeAt, history.get(0).getValidTo());
    }

    @Test
    void uniqueActiveConstraintPreventsSecondOpenRowForSameSupply() {
        SupplyEntity supply = persistSupply();
        SharingAgreementEntity agreement = persistPlantAndPublishedAgreement(supply);
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2025-01-01T00:00:00Z");
        persist(supply.getId(), agreement.getPlant().getId(), agreement.getId(), BigDecimal.valueOf(3.000000), t0, null);

        // Attempting to persist a second open-ended row for the same (plant, supply) must fail
        assertThrows(Exception.class, () -> {
            persist(supply.getId(), agreement.getPlant().getId(), agreement.getId(), BigDecimal.valueOf(4.000000), t1, null);
            // Force the flush so the DB constraint fires within this transaction
            jpaRepository.flush();
        });
    }

    // --- Commit 2: exclusion constraint coverage ---

    @Test
    void crossPlantOverlapIsAllowedForSameSupply() {
        SupplyEntity supply = persistSupply();
        SharingAgreementEntity agreement1 = persistPlantAndPublishedAgreement(supply);
        SharingAgreementEntity agreement2 = persistPlantAndPublishedAgreement(supply);
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2025-01-01T00:00:00Z");

        // Same supply, same [t0, t1) window, but two different plants -- must be allowed.
        persist(supply.getId(), agreement1.getPlant().getId(), agreement1.getId(), BigDecimal.valueOf(0.4), t0, t1);
        persist(supply.getId(), agreement2.getPlant().getId(), agreement2.getId(), BigDecimal.valueOf(0.6), t0, t1);

        assertDoesNotThrow(() -> jpaRepository.flush());
    }

    @Test
    void sameSupplyAndPlantBoundedOverlapIsRejectedByExclusionConstraint() {
        SupplyEntity supply = persistSupply();
        SharingAgreementEntity agreement = persistPlantAndPublishedAgreement(supply);
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2025-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2024-06-01T00:00:00Z");
        Instant t3 = Instant.parse("2025-06-01T00:00:00Z");
        persist(supply.getId(), agreement.getPlant().getId(), agreement.getId(), BigDecimal.valueOf(1.0), t0, t1);

        // [t2, t3) overlaps [t0, t1) even though neither row is open-ended.
        assertThrows(Exception.class, () -> {
            persist(supply.getId(), agreement.getPlant().getId(), agreement.getId(), BigDecimal.valueOf(2.0), t2, t3);
            jpaRepository.flush();
        });
    }

    @Test
    void adjacentBoundaryPeriodsAreAcceptedByExclusionConstraint() {
        SupplyEntity supply = persistSupply();
        SharingAgreementEntity agreement = persistPlantAndPublishedAgreement(supply);
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2025-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-01-01T00:00:00Z");

        // [t0, t1) then [t1, t2) share a boundary instant -- '[)' must treat them as adjacent, not overlapping.
        persist(supply.getId(), agreement.getPlant().getId(), agreement.getId(), BigDecimal.valueOf(1.0), t0, t1);
        persist(supply.getId(), agreement.getPlant().getId(), agreement.getId(), BigDecimal.valueOf(2.0), t1, t2);

        assertDoesNotThrow(() -> jpaRepository.flush());
    }

    @Test
    void exclusionConstraintPartialPredicatePinsValidFromIsNotNull() {
        // valid_from is still NOT NULL on every row in this phase, so the WHERE (valid_from IS NOT
        // NULL) predicate is currently a no-op -- it exists to future-proof the constraint against an
        // anticipated later relaxation of valid_from's nullability. This test pins the predicate's
        // presence so that future change doesn't silently drop it.
        String constraintDef = jdbcTemplate.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'no_overlapping_coefficients'",
                String.class);

        assertNotNull(constraintDef);
        assertTrue(constraintDef.contains("valid_from IS NOT NULL"), "unexpected constraint definition: " + constraintDef);
    }
}
