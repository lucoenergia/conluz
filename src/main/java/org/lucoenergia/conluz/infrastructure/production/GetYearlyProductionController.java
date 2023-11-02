package org.lucoenergia.conluz.infrastructure.production;

import org.apache.commons.lang3.StringUtils;
import org.lucoenergia.conluz.domain.production.GetProductionService;
import org.lucoenergia.conluz.domain.production.ProductionByTime;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/production/yearly")
public class GetYearlyProductionController {

    private final GetProductionService getProductionService;

    public GetYearlyProductionController(GetProductionService getProductionService) {
        this.getProductionService = getProductionService;
    }

    @GetMapping
    public List<ProductionByTime> getYearlyProduction(
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @RequestParam(required = false) String supplyId) {

        if (StringUtils.isBlank(supplyId)) {
            return getProductionService.getYearlyProductionByRangeOfDates(startDate, endDate);
        }
        return getProductionService.getYearlyProductionByRangeOfDatesAndSupply(startDate, endDate,
                new SupplyId(supplyId));
    }
}
