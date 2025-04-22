package org.lucoenergia.conluz.domain.production.get;

import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.production.InstantProduction;
import org.lucoenergia.conluz.domain.production.ProductionByTime;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Transactional(readOnly = true)
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

    public List<ProductionByTime> getHourlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate) {
        return getProductionRepository.getHourlyProductionByRangeOfDates(startDate, endDate);
    }

    public List<ProductionByTime> getHourlyProductionByRangeOfDatesAndSupply(OffsetDateTime startDate,
                                                                             OffsetDateTime endDate, SupplyId id) {
        Optional<Supply> supply = getSupplyRepository.findById(id);
        if (supply.isEmpty()) {
            throw new SupplyNotFoundException(id);
        }

        return getProductionRepository.getHourlyProductionByRangeOfDates(startDate,
                endDate, supply.get().getPartitionCoefficient());
    }

    public List<ProductionByTime> getDailyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate) {
        return getProductionRepository.getDailyProductionByRangeOfDates(startDate, endDate);
    }

    public List<ProductionByTime> getDailyProductionByRangeOfDatesAndSupply(OffsetDateTime startDate,
                                                                             OffsetDateTime endDate, SupplyId id) {
        Optional<Supply> supply = getSupplyRepository.findById(id);
        if (supply.isEmpty()) {
            throw new SupplyNotFoundException(id);
        }

        return getProductionRepository.getDailyProductionByRangeOfDates(startDate,
                endDate, supply.get().getPartitionCoefficient());
    }

    public List<ProductionByTime> getMonthlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate) {
        return getProductionRepository.getMonthlyProductionByRangeOfDates(startDate, endDate);
    }

    public List<ProductionByTime> getMonthlyProductionByRangeOfDatesAndSupply(OffsetDateTime startDate,
                                                                            OffsetDateTime endDate, SupplyId id) {
        Optional<Supply> supply = getSupplyRepository.findById(id);
        if (supply.isEmpty()) {
            throw new SupplyNotFoundException(id);
        }

        return getProductionRepository.getMonthlyProductionByRangeOfDates(startDate,
                endDate, supply.get().getPartitionCoefficient());
    }

    public List<ProductionByTime> getYearlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate) {
        return getProductionRepository.getYearlyProductionByRangeOfDates(startDate, endDate);
    }

    public List<ProductionByTime> getYearlyProductionByRangeOfDatesAndSupply(OffsetDateTime startDate,
                                                                              OffsetDateTime endDate, SupplyId id) {
        Optional<Supply> supply = getSupplyRepository.findById(id);
        if (supply.isEmpty()) {
            throw new SupplyNotFoundException(id);
        }

        return getProductionRepository.getYearlyProductionByRangeOfDates(startDate,
                endDate, supply.get().getPartitionCoefficient());
    }
}
