package org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientResolver;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.DuplicatePartitionCoefficientEntryException;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.MaterializeSharingAgreementCoefficientsService;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.PendingCoefficientEntry;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementNotDraftException;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionCoefficientEntity;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterializeSharingAgreementCoefficientsServiceImplTest extends BaseIntegrationTest {

    @Autowired
    private MaterializeSharingAgreementCoefficientsService materializeService;
    @Autowired
    private CoefficientResolver coefficientResolver;
    @Autowired
    private CommunityJpaRepository communityJpaRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SupplyRepository supplyRepository;
    @Autowired
    private PlantRepository plantRepository;
    @Autowired
    private SharingAgreementRepository sharingAgreementRepository;
    @Autowired
    private SupplyPartitionCoefficientJpaRepository supplyPartitionCoefficientJpaRepository;
    @Autowired
    private SaveSupplyPartitionCoefficientRepository supplyPartitionCoefficientRepository;

    private CommunityEntity persistCommunity() {
        return communityJpaRepository.save(CommunityMother.randomEntity().build());
    }

    private UserEntity persistUser() {
        return userRepository.save(UserMother.randomUserEntity());
    }

    private SupplyEntity persistSupply(UserEntity user, CommunityEntity community, String cups) {
        SupplyEntity supply = SupplyEntityMother.random(user, community);
        supply.setCode(cups);
        return supplyRepository.save(supply);
    }

    private PlantEntity persistPlant(SupplyEntity supply) {
        return plantRepository.save(PlantMother.randomPlantEntity().withSupply(supply).build());
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

    private List<SupplyPartitionCoefficientEntity> rowsFor(UUID sharingAgreementId) {
        return supplyPartitionCoefficientJpaRepository.findAll().stream()
                .filter(e -> e.getSharingAgreement().getId().equals(sharingAgreementId))
                .toList();
    }

    @Test
    void nonDraftAgreementThrowsAndLeavesSetUnchanged() {
        CommunityEntity community = persistCommunity();
        UserEntity user = persistUser();
        SupplyEntity supply = persistSupply(user, community, "ES0031300MATTEST010A");
        PlantEntity plant = persistPlant(supply);
        SharingAgreementEntity published = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);

        List<PendingCoefficientEntry> entries = List.of(new PendingCoefficientEntry(supply.getCode(), BigDecimal.ONE));

        assertThrows(SharingAgreementNotDraftException.class,
                () -> materializeService.replaceAll(plant.getId(), published.getId(), entries));

        assertTrue(rowsFor(published.getId()).isEmpty());
    }

    @Test
    void duplicateCupsThrowsAndLeavesSetUnchanged() {
        CommunityEntity community = persistCommunity();
        UserEntity user = persistUser();
        SupplyEntity supply = persistSupply(user, community, "ES0031300MATTEST020B");
        PlantEntity plant = persistPlant(supply);
        SharingAgreementEntity draft = persistAgreement(plant, SharingAgreementStatus.DRAFT);

        List<PendingCoefficientEntry> entries = List.of(
                new PendingCoefficientEntry(supply.getCode(), BigDecimal.valueOf(0.5)),
                new PendingCoefficientEntry(supply.getCode(), BigDecimal.valueOf(0.5)));

        assertThrows(DuplicatePartitionCoefficientEntryException.class,
                () -> materializeService.replaceAll(plant.getId(), draft.getId(), entries));

        assertTrue(rowsFor(draft.getId()).isEmpty());
    }

    @Test
    void unknownCupsThrowsAndLeavesSetUnchanged() {
        CommunityEntity community = persistCommunity();
        UserEntity user = persistUser();
        SupplyEntity supply = persistSupply(user, community, "ES0031300MATTEST030C");
        PlantEntity plant = persistPlant(supply);
        SharingAgreementEntity draft = persistAgreement(plant, SharingAgreementStatus.DRAFT);

        List<PendingCoefficientEntry> entries = List.of(
                new PendingCoefficientEntry(supply.getCode(), BigDecimal.valueOf(0.5)),
                new PendingCoefficientEntry("ES0031300UNKNOWNCUPS00", BigDecimal.valueOf(0.5)));

        assertThrows(SupplyNotFoundException.class,
                () -> materializeService.replaceAll(plant.getId(), draft.getId(), entries));

        assertTrue(rowsFor(draft.getId()).isEmpty());
    }

    @Test
    void crossCommunityCupsIsTreatedAsUnknown() {
        CommunityEntity communityA = persistCommunity();
        CommunityEntity communityB = persistCommunity();
        UserEntity user = persistUser();
        SupplyEntity supplyA = persistSupply(user, communityA, "ES0031300MATTEST040D");
        SupplyEntity supplyB = persistSupply(user, communityB, "ES0031300MATTEST050E");
        PlantEntity plantA = persistPlant(supplyA);
        SharingAgreementEntity draft = persistAgreement(plantA, SharingAgreementStatus.DRAFT);

        List<PendingCoefficientEntry> entries = List.of(
                new PendingCoefficientEntry(supplyB.getCode(), BigDecimal.ONE));

        assertThrows(SupplyNotFoundException.class,
                () -> materializeService.replaceAll(plantA.getId(), draft.getId(), entries));
    }

    @Test
    void sequentialReplaceAllCallsFullySupersedeEachOther() {
        CommunityEntity community = persistCommunity();
        UserEntity user = persistUser();
        SupplyEntity supply1 = persistSupply(user, community, "ES0031300MATTEST060F");
        SupplyEntity supply2 = persistSupply(user, community, "ES0031300MATTEST070G");
        PlantEntity plant = persistPlant(supply1);
        SharingAgreementEntity draft = persistAgreement(plant, SharingAgreementStatus.DRAFT);

        materializeService.replaceAll(plant.getId(), draft.getId(), List.of(
                new PendingCoefficientEntry(supply1.getCode(), BigDecimal.valueOf(0.5)),
                new PendingCoefficientEntry(supply2.getCode(), BigDecimal.valueOf(0.5))));
        assertEquals(2, rowsFor(draft.getId()).size());

        materializeService.replaceAll(plant.getId(), draft.getId(), List.of(
                new PendingCoefficientEntry(supply1.getCode(), BigDecimal.ONE)));

        List<SupplyPartitionCoefficientEntity> finalRows = rowsFor(draft.getId());
        assertEquals(1, finalRows.size());
        assertEquals(supply1.getId(), finalRows.get(0).getSupply().getId());
        assertEquals(0, BigDecimal.ONE.compareTo(finalRows.get(0).getCoefficient()));
    }

    @Test
    void midReplaceFailureLeavesPriorSetIntact() {
        // Exercises SaveSupplyPartitionCoefficientRepository.replaceAllForSharingAgreement directly
        // (below the service layer, which validates every CUPS before ever reaching the repository
        // in normal use) with a genuinely unpersistable second row -- a real failure, not a mocked
        // one -- to prove the delete-then-insert loop is atomic: nothing commits if any row fails.
        CommunityEntity community = persistCommunity();
        UserEntity user = persistUser();
        SupplyEntity supply1 = persistSupply(user, community, "ES0031300MATTEST080H");
        PlantEntity plant = persistPlant(supply1);
        SharingAgreementEntity draft = persistAgreement(plant, SharingAgreementStatus.DRAFT);

        materializeService.replaceAll(plant.getId(), draft.getId(), List.of(
                new PendingCoefficientEntry(supply1.getCode(), BigDecimal.valueOf(0.5))));

        SupplyPartitionCoefficient validRow = new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(supply1.getId())
                .withPlantId(plant.getId())
                .withSharingAgreementId(draft.getId())
                .withCoefficient(BigDecimal.valueOf(0.9))
                .withValidFrom(null)
                .withValidTo(null)
                .withCreatedAt(Instant.now())
                .build();
        SupplyPartitionCoefficient rowWithUnknownSupply = new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(UUID.randomUUID())
                .withPlantId(plant.getId())
                .withSharingAgreementId(draft.getId())
                .withCoefficient(BigDecimal.valueOf(0.1))
                .withValidFrom(null)
                .withValidTo(null)
                .withCreatedAt(Instant.now())
                .build();

        assertThrows(SupplyNotFoundException.class, () -> supplyPartitionCoefficientRepository
                .replaceAllForSharingAgreement(draft.getId(), List.of(validRow, rowWithUnknownSupply)));

        List<SupplyPartitionCoefficientEntity> rows = rowsFor(draft.getId());
        assertEquals(1, rows.size());
        assertEquals(0, BigDecimal.valueOf(0.5).compareTo(rows.get(0).getCoefficient()));
    }

    @Test
    void materializedRowsArePendingAndExcludedFromResolution() {
        CommunityEntity community = persistCommunity();
        UserEntity user = persistUser();
        SupplyEntity supply = persistSupply(user, community, "ES0031300MATTEST100J");
        PlantEntity plant = persistPlant(supply);
        SharingAgreementEntity draft = persistAgreement(plant, SharingAgreementStatus.DRAFT);

        materializeService.replaceAll(plant.getId(), draft.getId(), List.of(
                new PendingCoefficientEntry(supply.getCode(), BigDecimal.valueOf(0.6))));

        List<SupplyPartitionCoefficientEntity> rows = rowsFor(draft.getId());
        assertEquals(1, rows.size());
        assertNull(rows.get(0).getValidFrom());

        BigDecimal resolved = coefficientResolver.resolveCoefficient(plant.getId(), supply.getId(), Instant.now());
        assertEquals(0, BigDecimal.ZERO.compareTo(resolved));
    }

    @Test
    void pendingRowsForSameSupplyAcrossTwoAgreementsDoNotViolateExclusionConstraint() {
        // The no_overlapping_coefficients exclusion constraint's `WHERE (valid_from IS NOT NULL)`
        // predicate excludes pending rows from the overlap check entirely -- so the database allows
        // two pending rows for the same (plant, supply) from two different DRAFT agreements. This is
        // a documented, accepted gap (closed only within one replaceAll call, by the duplicate-CUPS
        // check), not a DB-level guarantee. This test proves the migration behaves as documented.
        CommunityEntity community = persistCommunity();
        UserEntity user = persistUser();
        SupplyEntity supply = persistSupply(user, community, "ES0031300MATTEST110K");
        PlantEntity plant = persistPlant(supply);
        SharingAgreementEntity draftA = persistAgreement(plant, SharingAgreementStatus.DRAFT);
        SharingAgreementEntity draftB = persistAgreement(plant, SharingAgreementStatus.DRAFT);

        materializeService.replaceAll(plant.getId(), draftA.getId(),
                List.of(new PendingCoefficientEntry(supply.getCode(), BigDecimal.valueOf(0.3))));
        materializeService.replaceAll(plant.getId(), draftB.getId(),
                List.of(new PendingCoefficientEntry(supply.getCode(), BigDecimal.valueOf(0.7))));

        assertEquals(1, rowsFor(draftA.getId()).size());
        assertEquals(1, rowsFor(draftB.getId()).size());
        assertFalse(rowsFor(draftA.getId()).isEmpty() && rowsFor(draftB.getId()).isEmpty());
    }
}
