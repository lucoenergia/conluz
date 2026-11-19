package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.lucoenergia.conluz.domain.admin.community.CommunityMother.DEFAULT_COMMUNITY_ID;

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
    private CreateSharingAgreementRepository createSharingAgreementRepository;

    private SupplyEntity persistSupply() {
        UserEntity user = UserMother.randomUserEntity();
        userRepository.save(user);
        return supplyRepository.save(SupplyEntityMother.random(
                user,
                communityJpaRepository.getReferenceById(DEFAULT_COMMUNITY_ID)
        ));
    }

    private CommunityEntity persistCommunity() {
        return communityJpaRepository.save(CommunityMother.randomEntity().build());
    }

    private SupplyEntity persistSupplyIn(CommunityEntity community) {
        UserEntity user = UserMother.randomUserEntity();
        userRepository.save(user);
        return supplyRepository.save(SupplyEntityMother.random(user, community));
    }

    /**
     * A plant belongs to a community through its own supply, so a plant "in" a community is one
     * whose supply is in it.
     */
    private PlantEntity persistPlantIn(CommunityEntity community) {
        return plantRepository.save(
                PlantMother.randomPlantEntity().withSupply(persistSupplyIn(community)).build());
    }

    private SharingAgreement persistPlantAndPublishedAgreement(SupplyEntity supply) {
        PlantEntity plant = plantRepository.save(PlantMother.randomPlantEntity().withSupply(supply).build());
        return persistPublishedAgreement(plant.getId());
    }

    private SharingAgreement persistPublishedAgreement(UUID plantId) {
        return persistAgreement(plantId, SharingAgreementStatus.PUBLISHED, Instant.now());
    }

    private SharingAgreement persistAgreement(UUID plantId, SharingAgreementStatus status, Instant createdAt) {
        return createSharingAgreementRepository.create(new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withPlantId(plantId)
                .withName("Test agreement " + UUID.randomUUID())
                .withStatus(status)
                .withCreatedAt(createdAt)
                .build());
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
    void findBySupplyIdInRangeReturnsOverlappingPeriods() {
        SupplyEntity supply = persistSupply();
        SharingAgreement agreement = persistPlantAndPublishedAgreement(supply);
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2025-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2025-06-01T00:00:00Z");
        persist(supply.getId(), agreement.getPlantId(), agreement.getId(), BigDecimal.valueOf(1.000000), t0, t1);
        // A supply holds one coefficient per agreement (#601): its next period belongs to another one
        SharingAgreement later = persistPublishedAgreement(agreement.getPlantId());
        persist(supply.getId(), agreement.getPlantId(), later.getId(), BigDecimal.valueOf(2.000000), t1, null);

        List<SupplyPartitionCoefficient> result = repository.findBySupplyIdInRange(
                supply.getId(), Instant.parse("2024-06-01T00:00:00Z"), t2);

        assertEquals(2, result.size());
    }

    @Test
    void findAllActiveAtTimestampReturnsOnlyActiveRows() {
        SupplyEntity supply1 = persistSupply();
        SharingAgreement agreement1 = persistPlantAndPublishedAgreement(supply1);
        SupplyEntity supply2 = persistSupply();
        SharingAgreement agreement2 = persistPlantAndPublishedAgreement(supply2);
        SupplyEntity supply3 = persistSupply();
        SharingAgreement agreement3 = persistPlantAndPublishedAgreement(supply3);

        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2025-01-01T00:00:00Z");
        Instant queryAt = Instant.parse("2025-06-01T00:00:00Z");

        persist(supply1.getId(), agreement1.getPlantId(), agreement1.getId(), BigDecimal.valueOf(10.0), t0, null);
        persist(supply2.getId(), agreement2.getPlantId(), agreement2.getId(), BigDecimal.valueOf(20.0), t0, null);
        // closed row — should not appear
        persist(supply3.getId(), agreement3.getPlantId(), agreement3.getId(), BigDecimal.valueOf(30.0), t0, t1);

        List<SupplyPartitionCoefficient> result = repository.findAllActiveAtTimestamp(queryAt);

        assertTrue(result.stream().anyMatch(c -> c.getSupplyId().equals(supply1.getId())));
        assertTrue(result.stream().anyMatch(c -> c.getSupplyId().equals(supply2.getId())));
        assertTrue(result.stream().noneMatch(c -> c.getValidTo() != null && c.getValidTo().isBefore(queryAt)));
    }

    @Test
    void findAllBySupplyIdOrderByValidFromAscReturnsChronologicalHistory() {
        SupplyEntity supply = persistSupply();
        SharingAgreement agreement = persistPlantAndPublishedAgreement(supply);
        Instant t0 = Instant.parse("2023-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2025-01-01T00:00:00Z");
        persist(supply.getId(), agreement.getPlantId(), agreement.getId(), BigDecimal.valueOf(1.000000), t0, t1);
        // A supply holds one coefficient per agreement (#601): its next period belongs to another one
        SharingAgreement later = persistPublishedAgreement(agreement.getPlantId());
        SharingAgreement latest = persistPublishedAgreement(agreement.getPlantId());
        persist(supply.getId(), agreement.getPlantId(), later.getId(), BigDecimal.valueOf(2.000000), t1, t2);
        persist(supply.getId(), agreement.getPlantId(), latest.getId(), BigDecimal.valueOf(3.000000), t2, null);

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
        SharingAgreement agreement1 = persistPlantAndPublishedAgreement(supply);
        // A supply is the supply of at most one plant (PLT-002): the second plant gets its own supply,
        // while both plants' coefficients stay on the shared consumer supply
        SharingAgreement agreement2 = persistPlantAndPublishedAgreement(persistSupply());
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        Instant queryAt = Instant.parse("2024-06-01T00:00:00Z");
        // Same supply, concurrently active on two different plants -- only the exclusion constraint's
        // (plant_id, supply_id) scoping (not supply_id alone) allows this.
        persist(supply.getId(), agreement1.getPlantId(), agreement1.getId(), BigDecimal.valueOf(0.4), t0, null);
        persist(supply.getId(), agreement2.getPlantId(), agreement2.getId(), BigDecimal.valueOf(0.6), t0, null);

        Optional<SupplyPartitionCoefficient> onPlant1 = repository.findByPlantIdAndSupplyIdAtTimestamp(
                agreement1.getPlantId(), supply.getId(), queryAt);
        Optional<SupplyPartitionCoefficient> onPlant2 = repository.findByPlantIdAndSupplyIdAtTimestamp(
                agreement2.getPlantId(), supply.getId(), queryAt);

        assertTrue(onPlant1.isPresent());
        assertEquals(0, BigDecimal.valueOf(0.4).compareTo(onPlant1.get().getCoefficient()));
        assertTrue(onPlant2.isPresent());
        assertEquals(0, BigDecimal.valueOf(0.6).compareTo(onPlant2.get().getCoefficient()));
    }

    @Test
    void findByPlantIdAndSupplyIdAtTimestampRespectsHalfOpenBoundary() {
        SupplyEntity supply = persistSupply();
        SharingAgreement agreement = persistPlantAndPublishedAgreement(supply);
        Instant changeAt = Instant.parse("2025-03-01T00:00:00Z");
        persist(supply.getId(), agreement.getPlantId(), agreement.getId(),
                BigDecimal.valueOf(1.0), Instant.parse("2024-01-01T00:00:00Z"), changeAt);
        // A supply holds one coefficient per agreement (#601): its next period belongs to another one
        SharingAgreement later = persistPublishedAgreement(agreement.getPlantId());
        persist(supply.getId(), agreement.getPlantId(), later.getId(),
                BigDecimal.valueOf(2.0), changeAt, null);

        Optional<SupplyPartitionCoefficient> atChange = repository.findByPlantIdAndSupplyIdAtTimestamp(
                agreement.getPlantId(), supply.getId(), changeAt);

        assertTrue(atChange.isPresent());
        assertEquals(0, BigDecimal.valueOf(2.0).compareTo(atChange.get().getCoefficient()));
    }

    @Test
    void findAllBySupplyIdAtTimestampReturnsEveryPlantActiveForThatSupply() {
        SupplyEntity supply = persistSupply();
        SharingAgreement agreement1 = persistPlantAndPublishedAgreement(supply);
        // A supply is the supply of at most one plant (PLT-002): the second plant gets its own supply,
        // while both plants' coefficients stay on the shared consumer supply
        SharingAgreement agreement2 = persistPlantAndPublishedAgreement(persistSupply());
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        Instant queryAt = Instant.parse("2024-06-01T00:00:00Z");
        persist(supply.getId(), agreement1.getPlantId(), agreement1.getId(), BigDecimal.valueOf(0.4), t0, null);
        persist(supply.getId(), agreement2.getPlantId(), agreement2.getId(), BigDecimal.valueOf(0.6), t0, null);

        List<SupplyPartitionCoefficient> result = repository.findAllBySupplyIdAtTimestamp(supply.getId(), queryAt);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(c -> c.getPlantId().equals(agreement1.getPlantId())));
        assertTrue(result.stream().anyMatch(c -> c.getPlantId().equals(agreement2.getPlantId())));
    }

    @Test
    void findAllBySupplyIdAtTimestampExcludesClosedPeriods() {
        SupplyEntity supply = persistSupply();
        SharingAgreement agreement = persistPlantAndPublishedAgreement(supply);
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2024-06-01T00:00:00Z");
        Instant queryAt = Instant.parse("2025-01-01T00:00:00Z");
        persist(supply.getId(), agreement.getPlantId(), agreement.getId(), BigDecimal.valueOf(0.4), t0, t1);

        List<SupplyPartitionCoefficient> result = repository.findAllBySupplyIdAtTimestamp(supply.getId(), queryAt);

        assertTrue(result.isEmpty());
    }

    // --- Coefficient activation (phase 5f) ---

    @Test
    void findAllByIdAndSharingAgreementIdExcludesCoefficientsFromAnotherAgreement() {
        SupplyEntity supply = persistSupply();
        SharingAgreement agreementA = persistPlantAndPublishedAgreement(supply);
        SharingAgreement agreementB = persistPublishedAgreement(agreementA.getPlantId());
        SupplyPartitionCoefficient inA = persist(supply.getId(), agreementA.getPlantId(), agreementA.getId(),
                BigDecimal.ONE, null, null);
        SupplyPartitionCoefficient inB = persist(supply.getId(), agreementB.getPlantId(), agreementB.getId(),
                BigDecimal.ONE, null, null);

        List<SupplyPartitionCoefficient> result = repository.findAllByIdAndSharingAgreementId(
                List.of(inA.getId(), inB.getId()), agreementA.getId());

        assertEquals(1, result.size());
        assertEquals(inA.getId(), result.get(0).getId());
    }

    @Test
    void findPredecessorReturnsRowEndingAtBoundaryWhenBoundaryNonNull() {
        SupplyEntity supply = persistSupply();
        SharingAgreement agreementA = persistPlantAndPublishedAgreement(supply);
        Instant boundary = Instant.parse("2025-01-01T00:00:00Z");
        SupplyPartitionCoefficient predecessor = persist(supply.getId(), agreementA.getPlantId(), agreementA.getId(),
                BigDecimal.ONE, Instant.parse("2024-01-01T00:00:00Z"), boundary);
        SharingAgreement agreementB = persistPublishedAgreement(agreementA.getPlantId());
        SupplyPartitionCoefficient successor = persist(supply.getId(), agreementB.getPlantId(), agreementB.getId(),
                BigDecimal.ONE, boundary, null);

        Optional<SupplyPartitionCoefficient> result = repository.findPredecessor(
                agreementA.getPlantId(), supply.getId(), successor.getId(), boundary);

        assertTrue(result.isPresent());
        assertEquals(predecessor.getId(), result.get().getId());
    }

    @Test
    void findNextActivatedAfterReturnsNearestLaterRowEvenWhenNotAdjacent() {
        SupplyEntity supply = persistSupply();
        SharingAgreement agreementA = persistPlantAndPublishedAgreement(supply);
        Instant closedAt = Instant.parse("2024-06-01T00:00:00Z");
        SupplyPartitionCoefficient closed = persist(supply.getId(), agreementA.getPlantId(), agreementA.getId(),
                BigDecimal.ONE, Instant.parse("2024-01-01T00:00:00Z"), closedAt);
        SharingAgreement agreementB = persistPublishedAgreement(agreementA.getPlantId());
        Instant laterStart = Instant.parse("2025-01-01T00:00:00Z"); // not adjacent to closedAt
        SupplyPartitionCoefficient later = persist(supply.getId(), agreementB.getPlantId(), agreementB.getId(),
                BigDecimal.ONE, laterStart, null);

        Optional<SupplyPartitionCoefficient> result = repository.findNextActivatedAfter(
                agreementA.getPlantId(), supply.getId(), closed.getId(), closed.getValidFrom());

        assertTrue(result.isPresent());
        assertEquals(later.getId(), result.get().getId());
    }

    @Test
    void findNextActivatedAfterReturnsEmptyWhenNoLaterRowExists() {
        SupplyEntity supply = persistSupply();
        SharingAgreement agreement = persistPlantAndPublishedAgreement(supply);
        SupplyPartitionCoefficient only = persist(supply.getId(), agreement.getPlantId(), agreement.getId(),
                BigDecimal.ONE, Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-06-01T00:00:00Z"));

        Optional<SupplyPartitionCoefficient> result = repository.findNextActivatedAfter(
                agreement.getPlantId(), supply.getId(), only.getId(), only.getValidFrom());

        assertTrue(result.isEmpty());
    }

    // --- Sharing-agreement coefficient set read (partition-coefficients GET) ---

    @Test
    void findAllBySharingAgreementIdReturnsEveryRowOfThatAgreement() {
        SupplyEntity supply1 = persistSupply();
        SupplyEntity supply2 = persistSupply();
        SharingAgreement agreementA = persistPlantAndPublishedAgreement(supply1);
        SharingAgreement agreementB = persistPublishedAgreement(agreementA.getPlantId());
        persist(supply1.getId(), agreementA.getPlantId(), agreementA.getId(), BigDecimal.valueOf(0.4), null, null);
        persist(supply2.getId(), agreementA.getPlantId(), agreementA.getId(), BigDecimal.valueOf(0.6), null, null);
        persist(supply1.getId(), agreementB.getPlantId(), agreementB.getId(), BigDecimal.ONE, null, null);

        List<SupplyPartitionCoefficient> result = repository.findAllBySharingAgreementId(agreementA.getId());

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(c -> c.getSharingAgreementId().equals(agreementA.getId())));
    }

    @Test
    void findAllBySharingAgreementIdReturnsEmptyWhenAgreementHasNoCoefficients() {
        SupplyEntity supply = persistSupply();
        SharingAgreement agreement = persistPlantAndPublishedAgreement(supply);

        List<SupplyPartitionCoefficient> result = repository.findAllBySharingAgreementId(agreement.getId());

        assertTrue(result.isEmpty());
    }

    // --- Revert-to-draft inert gate ---

    @Test
    void existsBySharingAgreementIdAndValidFromIsNotNullReturnsTrueWhenAtLeastOneRowIsApplied() {
        SupplyEntity supply1 = persistSupply();
        SupplyEntity supply2 = persistSupply();
        SharingAgreement agreement = persistPlantAndPublishedAgreement(supply1);
        // Pending row alongside an applied one -- the gate must not be fooled by the pending row.
        persist(supply1.getId(), agreement.getPlantId(), agreement.getId(), BigDecimal.ONE, null, null);
        persist(supply2.getId(), agreement.getPlantId(), agreement.getId(), BigDecimal.ONE,
                Instant.parse("2024-01-01T00:00:00Z"), null);

        assertTrue(repository.existsBySharingAgreementIdAndValidFromIsNotNull(agreement.getId()));
    }

    @Test
    void existsBySharingAgreementIdAndValidFromIsNotNullReturnsFalseWhenAllRowsArePending() {
        SupplyEntity supply1 = persistSupply();
        SupplyEntity supply2 = persistSupply();
        SharingAgreement agreement = persistPlantAndPublishedAgreement(supply1);
        persist(supply1.getId(), agreement.getPlantId(), agreement.getId(), BigDecimal.ONE, null, null);
        persist(supply2.getId(), agreement.getPlantId(), agreement.getId(), BigDecimal.ONE, null, null);

        assertFalse(repository.existsBySharingAgreementIdAndValidFromIsNotNull(agreement.getId()));
    }

    @Test
    void existsBySharingAgreementIdAndValidFromIsNotNullReturnsFalseWhenAgreementHasNoCoefficients() {
        SupplyEntity supply = persistSupply();
        SharingAgreement agreement = persistPlantAndPublishedAgreement(supply);

        assertFalse(repository.existsBySharingAgreementIdAndValidFromIsNotNull(agreement.getId()));
    }

    @Test
    void existsBySharingAgreementIdAndValidFromIsNotNullIgnoresValidToOnAPendingRow() {
        // A row can have validTo set (e.g. a defensive close) while validFrom is still null -- that
        // must not count as "applied": the gate keys on validFrom only, never validTo.
        SupplyEntity supply = persistSupply();
        SharingAgreement agreement = persistPlantAndPublishedAgreement(supply);
        persist(supply.getId(), agreement.getPlantId(), agreement.getId(), BigDecimal.ONE,
                null, Instant.parse("2025-01-01T00:00:00Z"));

        assertFalse(repository.existsBySharingAgreementIdAndValidFromIsNotNull(agreement.getId()));
    }

    // --- findEarliestValidFromByCommunityId ---

    /**
     * Several plants, several coefficients each, one pending row that is earlier than every
     * activated one, and an activated row in another community that is earlier still. Only the
     * earliest activated instant of this community's plants may come back, and none of the three
     * distractors can be excluded by accident: the pending row is the overall minimum, the other
     * community's row is the second minimum, and the answer lives on the second plant rather than
     * the first.
     */
    @Test
    void findEarliestValidFromByCommunityIdReturnsTheEarliestActivationAmongTheCommunityPlants() {
        CommunityEntity community = persistCommunity();
        CommunityEntity otherCommunity = persistCommunity();

        PlantEntity firstPlant = persistPlantIn(community);
        SharingAgreement firstAgreement = persistPublishedAgreement(firstPlant.getId());
        PlantEntity secondPlant = persistPlantIn(community);
        SharingAgreement secondAgreement = persistPublishedAgreement(secondPlant.getId());
        PlantEntity foreignPlant = persistPlantIn(otherCommunity);
        SharingAgreement foreignAgreement = persistPublishedAgreement(foreignPlant.getId());

        // The first plant's activated rows, both later than the answer.
        SupplyEntity consumerOne = persistSupplyIn(community);
        persist(consumerOne.getId(), firstPlant.getId(), firstAgreement.getId(), BigDecimal.ONE,
                Instant.parse("2024-06-01T00:00:00Z"), Instant.parse("2024-09-01T00:00:00Z"));
        // A supply holds one coefficient per agreement (#601): its next period belongs to another one
        SharingAgreement laterFirstAgreement = persistPublishedAgreement(firstPlant.getId());
        persist(consumerOne.getId(), firstPlant.getId(), laterFirstAgreement.getId(), BigDecimal.ONE,
                Instant.parse("2024-09-01T00:00:00Z"), null);

        // The answer: the earliest activated row of the community, on the second plant.
        SupplyEntity consumerTwo = persistSupplyIn(community);
        persist(consumerTwo.getId(), secondPlant.getId(), secondAgreement.getId(), BigDecimal.ONE,
                Instant.parse("2024-03-01T00:00:00Z"), null);

        // Earlier than the answer but never activated, so it must not count.
        SupplyEntity consumerThree = persistSupplyIn(community);
        persist(consumerThree.getId(), secondPlant.getId(), secondAgreement.getId(), BigDecimal.ONE,
                null, null);

        // Earlier than the answer but in another community, so it must not count either.
        SupplyEntity foreignConsumer = persistSupplyIn(otherCommunity);
        persist(foreignConsumer.getId(), foreignPlant.getId(), foreignAgreement.getId(), BigDecimal.ONE,
                Instant.parse("2023-01-01T00:00:00Z"), null);

        Optional<Instant> result = repository.findEarliestValidFromByCommunityId(community.getId());

        assertTrue(result.isPresent());
        assertEquals(Instant.parse("2024-03-01T00:00:00Z"), result.get());
    }

    /**
     * A community whose coefficients have all been authored but none applied has not started
     * saving anyone anything, which is empty rather than the earliest pending row.
     */
    @Test
    void findEarliestValidFromByCommunityIdIsEmptyWhenEveryCoefficientIsStillPending() {
        CommunityEntity community = persistCommunity();
        PlantEntity plant = persistPlantIn(community);
        SharingAgreement agreement = persistPublishedAgreement(plant.getId());

        SupplyEntity consumerOne = persistSupplyIn(community);
        persist(consumerOne.getId(), plant.getId(), agreement.getId(), BigDecimal.ONE, null, null);
        SupplyEntity consumerTwo = persistSupplyIn(community);
        persist(consumerTwo.getId(), plant.getId(), agreement.getId(), BigDecimal.ONE, null, null);

        assertTrue(repository.findEarliestValidFromByCommunityId(community.getId()).isEmpty());
    }

    @Test
    void findEarliestValidFromByCommunityIdIsEmptyForACommunityWithoutPlants() {
        CommunityEntity community = persistCommunity();

        assertTrue(repository.findEarliestValidFromByCommunityId(community.getId()).isEmpty());
    }

    @Test
    void findEarliestValidFromByCommunityIdIsEmptyForAnUnknownCommunity() {
        assertTrue(repository.findEarliestValidFromByCommunityId(UUID.randomUUID()).isEmpty());
    }
}
