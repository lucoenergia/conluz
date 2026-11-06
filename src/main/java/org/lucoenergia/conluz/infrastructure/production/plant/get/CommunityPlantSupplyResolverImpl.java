package org.lucoenergia.conluz.infrastructure.production.plant.get;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.production.plant.get.CommunityPlantSupplyResolver;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Matches the supply's CUPS against the CUPS of the supplies backing the community's plants.
 */
@Component
public class CommunityPlantSupplyResolverImpl implements CommunityPlantSupplyResolver {

    private final GetSupplyRepository getSupplyRepository;
    private final GetPlantRepository getPlantRepository;

    public CommunityPlantSupplyResolverImpl(GetSupplyRepository getSupplyRepository,
                                            GetPlantRepository getPlantRepository) {
        this.getSupplyRepository = getSupplyRepository;
        this.getPlantRepository = getPlantRepository;
    }

    @Override
    public Supply resolve(UUID communityId, SupplyId supplyId) {
        Set<String> communityPlantCups = getPlantRepository.findSupplyCodesByCommunity(communityId);
        Optional<Supply> supplyOptional = getSupplyRepository.findById(supplyId);
        // A supply that does not exist, or whose CUPS does not back a plant of this community, is
        // reported as 404 (never 403) so we do not confirm the existence of another community's supply.
        if (supplyOptional.isEmpty() || !communityPlantCups.contains(supplyOptional.get().getCode())) {
            throw new SupplyNotFoundException(supplyId);
        }
        return supplyOptional.get();
    }
}
