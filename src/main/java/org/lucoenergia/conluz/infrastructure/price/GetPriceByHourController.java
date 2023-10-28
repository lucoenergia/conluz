package org.lucoenergia.conluz.infrastructure.price;

import org.lucoenergia.conluz.domain.price.GetPriceService;
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

    private final GetPriceService getPriceService;

    public GetPriceByHourController(GetPriceService getPriceService) {
        this.getPriceService = getPriceService;
    }

    @GetMapping
    public List<PriceByHour> getPriceByRangeOfDates(
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate) {

        return getPriceService.getPricesByRangeOfDates(startDate, endDate);
    }
}
