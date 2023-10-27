package org.lucoenergia.conluz.infrastructure.price;

import org.lucoenergia.conluz.domain.price.GetPriceByHourService;
import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/prices")
public class GetPriceByHourController {

    private final GetPriceByHourService getPriceByHourService;

    public GetPriceByHourController(GetPriceByHourService getPriceByHourService) {
        this.getPriceByHourService = getPriceByHourService;
    }

    @GetMapping
    public List<PriceByHour> getPriceByRangeOfDates(
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate) {

        return getPriceByHourService.getPricesByRangeOfDates(startDate, endDate);
    }
}
