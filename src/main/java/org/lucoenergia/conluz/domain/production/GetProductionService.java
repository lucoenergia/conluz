package org.lucoenergia.conluz.domain.production;

import org.lucoenergia.conluz.domain.admin.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.Supply;
import org.lucoenergia.conluz.domain.admin.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class GetProductionService {

    private final GetProductionRepository getProductionRepository;
    private final GetSupplyRepository getSupplyRepository;

    public GetProductionService(GetProductionRepository getProductionRepository,
                                GetSupplyRepository getSupplyRepository) {
        this.getProductionRepository = getProductionRepository;
        this.getSupplyRepository = getSupplyRepository;
    }

    public InstantProduction getInstantProduction() {
        return getProductionRepository.getInstantProduction();
    }

    public InstantProduction getInstantProductionBySupply(SupplyId id) {

        Optional<Supply> supply = getSupplyRepository.findById(id);
        if (supply.isEmpty()) {
            throw new SupplyNotFoundException(id);
        }

        InstantProduction totalInstantProduction = getProductionRepository.getInstantProduction();

        return new InstantProduction(totalInstantProduction.getPower() * supply.get().getPartitionCoefficient());
    }

    public List<ProductionByHour> getHourlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate) {
        return getProductionRepository.getHourlyProductionByRangeOfDates(startDate, endDate);
    }
}
