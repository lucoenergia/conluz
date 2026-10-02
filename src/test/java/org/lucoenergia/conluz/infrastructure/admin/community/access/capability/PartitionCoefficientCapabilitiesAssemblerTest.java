package org.lucoenergia.conluz.infrastructure.admin.community.access.capability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CommunityReference;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.PlantReference;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SharingAgreementReference;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficientDetail;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyReference;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.access.AccessPolicies;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartitionCoefficientCapabilitiesAssemblerTest {

    @Mock
    private GetPlantRepository getPlantRepository;
    @Mock
    private GetSharingAgreementRepository getSharingAgreementRepository;

    private PartitionCoefficientCapabilitiesAssembler assembler() {
        return new PartitionCoefficientCapabilitiesAssembler(new AccessPolicies(), getPlantRepository,
                getSharingAgreementRepository);
    }

    // --- the caller matrix, over one period ---

    @Test
    void aCommunityAdminOfThePlantsCommunityMayOpenTheAgreement() {
        Scenario scenario = new Scenario();

        assertTrue(scenario.decide(CapabilityFixtures.adminOf(scenario.community)));
    }

    /**
     * A coefficient history is open to the supply owner, but the agreements behind it are admin-only:
     * owning the supply is not a reason to follow the reference.
     */
    @Test
    void theSupplyOwnerWhoIsAPlainMemberMayNotOpenTheAgreement() {
        Scenario scenario = new Scenario();

        assertFalse(scenario.decide(scenario.owner));
    }

    @Test
    void aPlainMemberMayNotOpenTheAgreement() {
        Scenario scenario = new Scenario();

        assertFalse(scenario.decide(CapabilityFixtures.memberOf(scenario.community)));
    }

    @Test
    void anAdminOfAnotherCommunityMayNotOpenTheAgreement() {
        Scenario scenario = new Scenario();

        assertFalse(scenario.decide(CapabilityFixtures.adminOf(CapabilityFixtures.community())));
    }

    /**
     * Plant visibility runs through membership, so a platform admin outside the plant's community
     * cannot see the plant, let alone its agreements.
     */
    @Test
    void aPlatformAdminOutsideTheCommunityMayNotOpenTheAgreement() {
        Scenario scenario = new Scenario();

        assertFalse(scenario.decide(CapabilityFixtures.platformAdmin()));
    }

    // --- per plant, and the degenerate states ---

    /**
     * A history spanning several plants answers per plant: the caller administers one plant's
     * community and not the other's.
     */
    @Test
    void aHistorySpanningSeveralPlantsIsAnsweredPerPlant() {
        Community administered = CapabilityFixtures.community();
        Community other = CapabilityFixtures.community();
        User caller = CapabilityFixtures.adminOf(administered);
        Plant ownPlant = CapabilityFixtures.plantOf(CapabilityFixtures.supplyIn(administered, caller));
        Plant foreignPlant = CapabilityFixtures.plantOf(CapabilityFixtures.supplyIn(other, caller));
        SharingAgreement ownAgreement = CapabilityFixtures.agreementOf(ownPlant);
        SharingAgreement foreignAgreement = CapabilityFixtures.agreementOf(foreignPlant);
        SupplyPartitionCoefficientDetail inOwnPlant = detail(ownPlant.getId(), ownAgreement.getId());
        SupplyPartitionCoefficientDetail inForeignPlant = detail(foreignPlant.getId(), foreignAgreement.getId());
        when(getPlantRepository.findAllByIds(anyCollection())).thenReturn(List.of(ownPlant, foreignPlant));
        when(getSharingAgreementRepository.findAllByIdsWithoutFile(anyCollection()))
                .thenReturn(List.of(ownAgreement, foreignAgreement));

        Map<UUID, PartitionCoefficientCapabilitiesResponse> capabilities =
                assembler().assembleAll(caller, List.of(inOwnPlant, inForeignPlant));

        assertTrue(capabilities.get(inOwnPlant.getId()).isCanReadSharingAgreement());
        assertFalse(capabilities.get(inForeignPlant.getId()).isCanReadSharingAgreement());
    }

    @Test
    void aPlantThatNoLongerResolvesAnswersFalse() {
        Scenario scenario = new Scenario();
        when(getPlantRepository.findAllByIds(anyCollection())).thenReturn(List.of());
        when(getSharingAgreementRepository.findAllByIdsWithoutFile(anyCollection()))
                .thenReturn(List.of(scenario.agreement));

        Map<UUID, PartitionCoefficientCapabilitiesResponse> capabilities = assembler()
                .assembleAll(CapabilityFixtures.adminOf(scenario.community), List.of(scenario.detail));

        assertFalse(capabilities.get(scenario.detail.getId()).isCanReadSharingAgreement());
    }

    @Test
    void anAgreementThatNoLongerResolvesAnswersFalse() {
        Scenario scenario = new Scenario();
        when(getPlantRepository.findAllByIds(anyCollection())).thenReturn(List.of(scenario.plant));
        when(getSharingAgreementRepository.findAllByIdsWithoutFile(anyCollection())).thenReturn(List.of());

        Map<UUID, PartitionCoefficientCapabilitiesResponse> capabilities = assembler()
                .assembleAll(CapabilityFixtures.adminOf(scenario.community), List.of(scenario.detail));

        assertFalse(capabilities.get(scenario.detail.getId()).isCanReadSharingAgreement());
    }

    /**
     * The reason the capability follows the agreement endpoint's rule rather than the plant's: a link
     * to an agreement under a different plant would be a 404 however privileged the caller is.
     */
    @Test
    void anAgreementBelongingToAnotherPlantAnswersFalse() {
        Scenario scenario = new Scenario();
        Plant otherPlant = CapabilityFixtures.plantOf(
                CapabilityFixtures.supplyIn(scenario.community, scenario.owner));
        SharingAgreement agreementOfOtherPlant = CapabilityFixtures.agreementOf(otherPlant);
        SupplyPartitionCoefficientDetail mismatched = detail(scenario.plant.getId(), agreementOfOtherPlant.getId());
        when(getPlantRepository.findAllByIds(anyCollection())).thenReturn(List.of(scenario.plant));
        when(getSharingAgreementRepository.findAllByIdsWithoutFile(anyCollection()))
                .thenReturn(List.of(agreementOfOtherPlant));

        Map<UUID, PartitionCoefficientCapabilitiesResponse> capabilities = assembler()
                .assembleAll(CapabilityFixtures.adminOf(scenario.community), List.of(mismatched));

        assertFalse(capabilities.get(mismatched.getId()).isCanReadSharingAgreement());
    }

    // --- cost and shape ---

    @Test
    void manyPeriodsAcrossSeveralPlantsCostOneLookupOfEachKind() {
        Community community = CapabilityFixtures.community();
        User caller = CapabilityFixtures.adminOf(community);
        List<Plant> plants = new ArrayList<>();
        List<SharingAgreement> agreements = new ArrayList<>();
        List<SupplyPartitionCoefficientDetail> details = new ArrayList<>();
        for (int p = 0; p < 3; p++) {
            Plant plant = CapabilityFixtures.plantOf(CapabilityFixtures.supplyIn(community, caller));
            SharingAgreement agreement = CapabilityFixtures.agreementOf(plant);
            plants.add(plant);
            agreements.add(agreement);
            for (int period = 0; period < 10; period++) {
                details.add(detail(plant.getId(), agreement.getId()));
            }
        }
        when(getPlantRepository.findAllByIds(anyCollection())).thenReturn(plants);
        when(getSharingAgreementRepository.findAllByIdsWithoutFile(anyCollection())).thenReturn(agreements);

        Map<UUID, PartitionCoefficientCapabilitiesResponse> capabilities = assembler().assembleAll(caller, details);

        assertEquals(30, capabilities.size());
        capabilities.values().forEach(value -> assertTrue(value.isCanReadSharingAgreement()));
        verify(getPlantRepository, times(1)).findAllByIds(anyCollection());
        verify(getSharingAgreementRepository, times(1)).findAllByIdsWithoutFile(anyCollection());
    }

    @Test
    void anEmptyListLoadsNothing() {
        Map<UUID, PartitionCoefficientCapabilitiesResponse> capabilities =
                assembler().assembleAll(CapabilityFixtures.stranger(), List.of());

        assertTrue(capabilities.isEmpty());
        verify(getPlantRepository, never()).findAllByIds(anyCollection());
        verify(getSharingAgreementRepository, never()).findAllByIdsWithoutFile(anyCollection());
    }

    /**
     * The map is keyed by coefficient id, so a repeated id would silently hand one period another's
     * answer. It is refused instead.
     */
    @Test
    void aCoefficientIdRepeatedInOneResponseIsRefused() {
        Scenario scenario = new Scenario();
        when(getPlantRepository.findAllByIds(anyCollection())).thenReturn(List.of(scenario.plant));
        when(getSharingAgreementRepository.findAllByIdsWithoutFile(anyCollection()))
                .thenReturn(List.of(scenario.agreement));

        assertThrows(IllegalStateException.class, () -> assembler().assembleAll(
                CapabilityFixtures.adminOf(scenario.community), List.of(scenario.detail, scenario.detail)));
    }

    /**
     * One plant and one agreement in a community, with the supply owned by a plain member.
     */
    private final class Scenario {

        private final Community community = CapabilityFixtures.community();
        private final User owner = CapabilityFixtures.memberOf(community);
        private final Supply supply = CapabilityFixtures.supplyIn(community, owner);
        private final Plant plant = CapabilityFixtures.plantOf(supply);
        private final SharingAgreement agreement = CapabilityFixtures.agreementOf(plant);
        private final SupplyPartitionCoefficientDetail detail = detail(plant.getId(), agreement.getId());

        private boolean decide(User caller) {
            when(getPlantRepository.findAllByIds(anyCollection())).thenReturn(List.of(plant));
            when(getSharingAgreementRepository.findAllByIdsWithoutFile(anyCollection())).thenReturn(List.of(agreement));
            return assembler().assembleAll(caller, List.of(detail)).get(detail.getId()).isCanReadSharingAgreement();
        }
    }

    private static SupplyPartitionCoefficientDetail detail(UUID plantId, UUID agreementId) {
        UUID supplyId = UUID.randomUUID();
        return new SupplyPartitionCoefficientDetail(
                new SupplyPartitionCoefficient.Builder()
                        .withId(UUID.randomUUID())
                        .withSupplyId(supplyId)
                        .withPlantId(plantId)
                        .withSharingAgreementId(agreementId)
                        .withCoefficient(BigDecimal.valueOf(0.5))
                        .withValidFrom(Instant.parse("2025-01-01T00:00:00Z"))
                        .withCreatedAt(Instant.now())
                        .build(),
                new SupplyReference(supplyId, "ES0031607648137001RC0F", "A supply"),
                new CommunityReference(UUID.randomUUID(), "A community"),
                new PlantReference(plantId, "A plant"),
                new SharingAgreementReference(agreementId, "An agreement", SharingAgreementStatus.PUBLISHED));
    }
}
