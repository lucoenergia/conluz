package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficientDetail;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;

/**
 * Covers the detail projections behind the multi-plant read API, including the N+1 guard: the whole
 * point of a constructor expression here is that a result set costs one query regardless of its size.
 */
@Transactional
class GetSupplyPartitionCoefficientDetailRepositoryDatabaseTest extends BaseIntegrationTest {

    @Autowired
    private GetSupplyPartitionCoefficientRepository repository;
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

    @PersistenceContext
    private EntityManager entityManager;

    private static final Instant T0 = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2025-01-01T00:00:00Z");

    @Test
    void findAllDetailsCarriesSupplyPlantAndAgreementReferences() {
        SupplyEntity supply = persistSupply();
        SharingAgreementEntity agreement = persistPlantAndAgreement(supply, SharingAgreementStatus.PUBLISHED);
        persist(supply, agreement, BigDecimal.valueOf(0.250000), T0, null);

        List<SupplyPartitionCoefficientDetail> details = repository.findAllDetailsBySupplyId(supply.getId(), null);

        assertEquals(1, details.size());
        SupplyPartitionCoefficientDetail detail = details.get(0);
        assertEquals(supply.getId(), detail.getSupply().id());
        assertEquals(supply.getCode(), detail.getSupply().code());
        assertEquals(agreement.getPlant().getId(), detail.getPlant().id());
        assertEquals(agreement.getPlant().getName(), detail.getPlant().name());
        assertEquals(agreement.getId(), detail.getSharingAgreement().id());
        assertEquals(agreement.getName(), detail.getSharingAgreement().name());
        assertEquals(SharingAgreementStatus.PUBLISHED, detail.getSharingAgreement().status());
        assertNotNull(detail.getCoefficient());
    }

    @Test
    void findAllDetailsFiltersByPlantWhenAPlantIsGiven() {
        SupplyEntity supply = persistSupply();
        SharingAgreementEntity inX = persistPlantAndAgreement(supply, SharingAgreementStatus.PUBLISHED);
        SharingAgreementEntity inY = persistPlantAndAgreement(supply, SharingAgreementStatus.PUBLISHED);
        persist(supply, inX, BigDecimal.valueOf(0.400000), T0, null);
        persist(supply, inY, BigDecimal.valueOf(0.600000), T0, null);

        UUID plantX = inX.getPlant().getId();
        List<SupplyPartitionCoefficientDetail> filtered = repository.findAllDetailsBySupplyId(supply.getId(), plantX);
        List<SupplyPartitionCoefficientDetail> unfiltered = repository.findAllDetailsBySupplyId(supply.getId(), null);

        assertEquals(1, filtered.size());
        assertEquals(plantX, filtered.get(0).getPlant().id());
        assertEquals(2, unfiltered.size());
    }

    @Test
    void findAllDetailsReturnsEmptyForAPlantTheSupplyHasNoCoefficientIn() {
        SupplyEntity supply = persistSupply();
        SharingAgreementEntity agreement = persistPlantAndAgreement(supply, SharingAgreementStatus.PUBLISHED);
        persist(supply, agreement, BigDecimal.valueOf(1.000000), T0, null);
        PlantEntity unrelated = plantRepository.save(
                PlantMother.randomPlantEntity().withSupply(persistSupply()).build());

        assertTrue(repository.findAllDetailsBySupplyId(supply.getId(), unrelated.getId()).isEmpty());
    }

    @Test
    void findActiveDetailsExcludesPendingRowsAndReturnsOnePerPlant() {
        SupplyEntity supply = persistSupply();
        SharingAgreementEntity inX = persistPlantAndAgreement(supply, SharingAgreementStatus.PUBLISHED);
        SharingAgreementEntity inY = persistPlantAndAgreement(supply, SharingAgreementStatus.PUBLISHED);
        SharingAgreementEntity draftInX = persistAgreement(inX.getPlant(), SharingAgreementStatus.DRAFT);
        persist(supply, inX, BigDecimal.valueOf(0.400000), T0, null);
        persist(supply, inY, BigDecimal.valueOf(0.600000), T0, null);
        // Pending: authored but never applied. Its validTo is null too, so only the validFrom
        // predicate separates it from a genuinely active row.
        persist(supply, draftInX, BigDecimal.valueOf(0.900000), null, null);

        List<SupplyPartitionCoefficientDetail> active = repository.findActiveDetailsBySupplyId(supply.getId(), null);

        assertEquals(2, active.size());
        assertTrue(active.stream().allMatch(d -> d.getValidFrom() != null));
    }

    @Test
    void findActiveDetailsReturnsEmptyWhenOnlyPendingRowsExist() {
        SupplyEntity supply = persistSupply();
        SharingAgreementEntity draft = persistPlantAndAgreement(supply, SharingAgreementStatus.DRAFT);
        persist(supply, draft, BigDecimal.valueOf(1.000000), null, null);

        assertTrue(repository.findActiveDetailsBySupplyId(supply.getId(), null).isEmpty());
    }

    @Test
    void findDetailsAtTimestampTreatsValidFromAsInclusiveAndValidToAsExclusive() {
        SupplyEntity supply = persistSupply();
        SharingAgreementEntity agreement = persistPlantAndAgreement(supply, SharingAgreementStatus.PUBLISHED);
        persist(supply, agreement, BigDecimal.valueOf(1.000000), T0, T1);
        persist(supply, agreement, BigDecimal.valueOf(2.000000), T1, null);

        // At the shared boundary the later period applies: validTo is exclusive, validFrom inclusive.
        List<SupplyPartitionCoefficientDetail> atBoundary =
                repository.findDetailsBySupplyIdAtTimestamp(supply.getId(), null, T1);
        List<SupplyPartitionCoefficientDetail> justBefore =
                repository.findDetailsBySupplyIdAtTimestamp(supply.getId(), null, T1.minusMillis(1));

        assertEquals(1, atBoundary.size());
        assertEquals(0, BigDecimal.valueOf(2.000000).compareTo(atBoundary.get(0).getCoefficientValue()));
        assertEquals(1, justBefore.size());
        assertEquals(0, BigDecimal.valueOf(1.000000).compareTo(justBefore.get(0).getCoefficientValue()));
    }

    @Test
    void findDetailsAtTimestampReturnsOneItemPerPlantAndHonoursThePlantFilter() {
        SupplyEntity supply = persistSupply();
        SharingAgreementEntity inX = persistPlantAndAgreement(supply, SharingAgreementStatus.PUBLISHED);
        SharingAgreementEntity inY = persistPlantAndAgreement(supply, SharingAgreementStatus.PUBLISHED);
        persist(supply, inX, BigDecimal.valueOf(0.400000), T0, null);
        persist(supply, inY, BigDecimal.valueOf(0.600000), T0, null);
        Instant inside = Instant.parse("2024-06-15T12:00:00Z");

        assertEquals(2, repository.findDetailsBySupplyIdAtTimestamp(supply.getId(), null, inside).size());
        assertEquals(1, repository.findDetailsBySupplyIdAtTimestamp(supply.getId(), inX.getPlant().getId(), inside).size());
    }

    @Test
    void findActiveDetailsByPlantIdAndSupplyIdInResolvesABatchInOneCall() {
        SupplyEntity supplyA = persistSupply();
        SupplyEntity supplyB = persistSupply();
        SharingAgreementEntity agreement = persistPlantAndAgreement(supplyA, SharingAgreementStatus.PUBLISHED);
        persist(supplyA, agreement, BigDecimal.valueOf(0.300000), T0, null);
        persist(supplyB, agreement, BigDecimal.valueOf(0.700000), T0, null);

        List<SupplyPartitionCoefficientDetail> details = repository.findActiveDetailsByPlantIdAndSupplyIdIn(
                agreement.getPlant().getId(), List.of(supplyA.getId(), supplyB.getId()));

        assertEquals(2, details.size());
    }

    /**
     * The N+1 guard. Comparing the statement count at two different row counts is what proves the
     * absence of N+1 -- a single absolute number would pass just as happily if the baseline were
     * already per-row.
     */
    @Test
    void readingTheHistoryCostsTheSameNumberOfQueriesRegardlessOfRowCount() {
        SupplyEntity oneRowSupply = persistSupply();
        SharingAgreementEntity oneRowAgreement = persistPlantAndAgreement(oneRowSupply, SharingAgreementStatus.PUBLISHED);
        persist(oneRowSupply, oneRowAgreement, BigDecimal.valueOf(1.000000), T0, null);

        SupplyEntity manyRowSupply = persistSupply();
        SharingAgreementEntity inX = persistPlantAndAgreement(manyRowSupply, SharingAgreementStatus.PUBLISHED);
        SharingAgreementEntity inY = persistPlantAndAgreement(manyRowSupply, SharingAgreementStatus.PUBLISHED);
        persist(manyRowSupply, inX, BigDecimal.valueOf(0.100000), T0, T1);
        persist(manyRowSupply, inX, BigDecimal.valueOf(0.200000), T1, null);
        persist(manyRowSupply, inY, BigDecimal.valueOf(0.700000), T0, null);

        long oneRowCost = countStatements(() -> {
            List<SupplyPartitionCoefficientDetail> details =
                    repository.findAllDetailsBySupplyId(oneRowSupply.getId(), null);
            assertEquals(1, details.size());
            touchEveryReference(details);
        });

        long threeRowsAcrossTwoPlantsCost = countStatements(() -> {
            List<SupplyPartitionCoefficientDetail> details =
                    repository.findAllDetailsBySupplyId(manyRowSupply.getId(), null);
            assertEquals(3, details.size());
            touchEveryReference(details);
        });

        assertEquals(oneRowCost, threeRowsAcrossTwoPlantsCost,
                "Reading three rows across two plants must not cost more queries than reading one row");
        assertEquals(1, oneRowCost, "A detail projection must be a single query");
    }

    /**
     * Dereferences everything the response layer will touch, so a lazily-resolved association would
     * show up in the statement count rather than hiding behind an untouched proxy.
     */
    private void touchEveryReference(List<SupplyPartitionCoefficientDetail> details) {
        for (SupplyPartitionCoefficientDetail detail : details) {
            assertNotNull(detail.getSupply().code());
            assertNotNull(detail.getPlant().name());
            assertNotNull(detail.getSharingAgreement().name());
            assertNotNull(detail.getSharingAgreement().status());
        }
    }

    private long countStatements(Runnable work) {
        // Everything written by the fixture must reach the database before counting starts, or the
        // flush would be billed to the read under test.
        entityManager.flush();
        Statistics statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        long before = statistics.getPrepareStatementCount();
        work.run();
        return statistics.getPrepareStatementCount() - before;
    }

    private SupplyEntity persistSupply() {
        UserEntity user = UserMother.randomUserEntity();
        userRepository.save(user);
        return supplyRepository.save(SupplyEntityMother.random(
                user, communityJpaRepository.getReferenceById(DEFAULT_COMMUNITY_ID)));
    }

    private SharingAgreementEntity persistPlantAndAgreement(SupplyEntity supply, SharingAgreementStatus status) {
        PlantEntity plant = plantRepository.save(PlantMother.randomPlantEntity().withSupply(supply).build());
        return persistAgreement(plant, status);
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

    private void persist(SupplyEntity supply, SharingAgreementEntity agreement, BigDecimal coefficient,
                         Instant validFrom, Instant validTo) {
        saveRepository.save(new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(supply.getId())
                .withPlantId(agreement.getPlant().getId())
                .withSharingAgreementId(agreement.getId())
                .withCoefficient(coefficient)
                .withValidFrom(validFrom)
                .withValidTo(validTo)
                .withCreatedAt(Instant.now())
                .build());
    }
}
