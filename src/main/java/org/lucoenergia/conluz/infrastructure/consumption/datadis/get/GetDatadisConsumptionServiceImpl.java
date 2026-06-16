package org.lucoenergia.conluz.infrastructure.consumption.datadis.get;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionService;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class GetDatadisConsumptionServiceImpl implements GetDatadisConsumptionService {

    private final GetDatadisConsumptionRepository getDatadisConsumptionRepository;
    private final GetSupplyRepository getSupplyRepository;

    public GetDatadisConsumptionServiceImpl(
            @Qualifier("getDatadisConsumptionRepositoryInflux") GetDatadisConsumptionRepository getDatadisConsumptionRepository,
            GetSupplyRepository getSupplyRepository) {
        this.getDatadisConsumptionRepository = getDatadisConsumptionRepository;
        this.getSupplyRepository = getSupplyRepository;
    }

    @Override
    public List<DatadisConsumption> getDailyConsumptionBySupply(SupplyId supplyId, OffsetDateTime startDate,
                                                                OffsetDateTime endDate) {
        Supply supply = getSupplyOrThrow(supplyId);
        return getDatadisConsumptionRepository.getDailyConsumptionsByRangeOfDates(supply, startDate, endDate);
    }

    @Override
    public List<DatadisConsumption> getHourlyConsumptionBySupply(SupplyId supplyId, OffsetDateTime startDate,
                                                                 OffsetDateTime endDate) {
        Supply supply = getSupplyOrThrow(supplyId);
        return getDatadisConsumptionRepository.getHourlyConsumptionsByRangeOfDates(supply, startDate, endDate);
    }

    @Override
    public List<DatadisConsumption> getMonthlyConsumptionBySupply(SupplyId supplyId, OffsetDateTime startDate,
                                                                   OffsetDateTime endDate) {
        Supply supply = getSupplyOrThrow(supplyId);
        return getDatadisConsumptionRepository.getMonthlyConsumptionsByRangeOfDates(supply, startDate, endDate);
    }

    @Override
    public List<DatadisConsumption> getYearlyConsumptionBySupply(SupplyId supplyId, OffsetDateTime startDate,
                                                                  OffsetDateTime endDate) {
        Supply supply = getSupplyOrThrow(supplyId);
        return getDatadisConsumptionRepository.getYearlyConsumptionsByRangeOfDates(supply, startDate, endDate);
    }

    private Supply getSupplyOrThrow(SupplyId supplyId) {
        Optional<Supply> supplyOptional = getSupplyRepository.findById(supplyId);
        if (supplyOptional.isEmpty()) {
            throw new SupplyNotFoundException(supplyId);
        }
        return supplyOptional.get();
    }
}
