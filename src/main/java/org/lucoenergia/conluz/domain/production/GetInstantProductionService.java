package org.lucoenergia.conluz.domain.production;

import org.lucoenergia.conluz.domain.admin.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.Supply;
import org.lucoenergia.conluz.domain.admin.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class GetInstantProductionService {

    private final GetInstantProductionRepository getInstantProductionRepository;
    private final GetSupplyRepository getSupplyRepository;

    public GetInstantProductionService(GetInstantProductionRepository getInstantProductionRepository,
                                       GetSupplyRepository getSupplyRepository) {
        this.getInstantProductionRepository = getInstantProductionRepository;
        this.getSupplyRepository = getSupplyRepository;
    }

    public InstantProduction getInstantProduction() {
        return getInstantProductionRepository.getInstantProduction();
    }

    public InstantProduction getInstantProductionBySupply(SupplyId id) {

        Optional<Supply> supply = getSupplyRepository.findById(id);
        if (supply.isEmpty()) {
            throw new SupplyNotFoundException(id);
        }

        InstantProduction totalInstantProduction = getInstantProductionRepository.getInstantProduction();

        return new InstantProduction(totalInstantProduction.getPower() * supply.get().getPartitionCoefficient());
    }
}
