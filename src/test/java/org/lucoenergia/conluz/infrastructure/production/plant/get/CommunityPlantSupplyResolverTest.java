package org.lucoenergia.conluz.infrastructure.production.plant.get;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.production.plant.get.CommunityPlantSupplyResolver;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityPlantSupplyResolverTest {

    @Mock
    private GetSupplyRepository getSupplyRepository;
    @Mock
    private GetPlantRepository getPlantRepository;

    private final UUID communityId = UUID.randomUUID();

    private CommunityPlantSupplyResolver resolver() {
        return new CommunityPlantSupplyResolverImpl(getSupplyRepository, getPlantRepository);
    }

    @Test
    void resolve_returnsTheSupply_whenItBacksAPlantOfTheCommunity() {
        Supply supply = SupplyMother.random().withCode("CUPS_B").build();
        SupplyId supplyId = SupplyId.of(supply.getId());
        when(getPlantRepository.findSupplyCodesByCommunity(communityId)).thenReturn(Set.of("CUPS_A", "CUPS_B"));
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(supply));

        assertSame(supply, resolver().resolve(communityId, supplyId));
    }

    @Test
    void resolve_throwsSupplyNotFound_whenTheSupplyDoesNotExist() {
        SupplyId supplyId = SupplyId.of(UUID.randomUUID());
        when(getPlantRepository.findSupplyCodesByCommunity(communityId)).thenReturn(Set.of("CUPS_A"));
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.empty());

        assertThrows(SupplyNotFoundException.class, () -> resolver().resolve(communityId, supplyId));
    }

    @Test
    void resolve_throwsSupplyNotFound_whenTheSupplyBacksNoPlantOfTheCommunity() {
        // A consumer supply of the community, or a supply of another community: its CUPS is not
        // among the CUPS backing the community's plants.
        Supply supply = SupplyMother.random().withCode("CUPS_CONSUMER").build();
        SupplyId supplyId = SupplyId.of(supply.getId());
        when(getPlantRepository.findSupplyCodesByCommunity(communityId)).thenReturn(Set.of("CUPS_A", "CUPS_B"));
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(supply));

        assertThrows(SupplyNotFoundException.class, () -> resolver().resolve(communityId, supplyId));
    }

    @Test
    void resolve_throwsSupplyNotFound_whenTheCommunityHasNoPlants() {
        Supply supply = SupplyMother.random().withCode("CUPS_A").build();
        SupplyId supplyId = SupplyId.of(supply.getId());
        when(getPlantRepository.findSupplyCodesByCommunity(communityId)).thenReturn(Set.of());
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(supply));

        assertThrows(SupplyNotFoundException.class, () -> resolver().resolve(communityId, supplyId));
    }
}
