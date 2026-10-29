package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.PlantReference;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Resolves the plant a supply response references (SUP-004): the plant whose own supply it is, or
 * {@code null}. Every method issues <strong>one</strong> query, for a whole page when given one, never one
 * per supply.
 */
@Component
public class SupplyPlantAssembler {

    private final GetPlantRepository getPlantRepository;

    public SupplyPlantAssembler(GetPlantRepository getPlantRepository) {
        this.getPlantRepository = getPlantRepository;
    }

    /**
     * The plant whose own supply this is, or {@code null} when it is no plant's own supply.
     */
    public PlantReference plantOf(Supply supply) {
        return plantsOf(List.of(supply)).apply(supply);
    }

    /**
     * The plant of each of the supplies, read in one query for all of them. The function answers
     * {@code null} for a supply that is no plant's own supply.
     */
    public Function<Supply, PlantReference> plantsOf(Collection<Supply> supplies) {
        Map<UUID, PlantReference> plants = getPlantRepository.findReferencesBySupplies(
                supplies.stream().map(supply -> SupplyId.of(supply.getId())).toList());
        return supply -> plants.get(supply.getId());
    }
}
