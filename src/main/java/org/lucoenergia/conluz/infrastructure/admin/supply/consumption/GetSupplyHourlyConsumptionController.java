package org.lucoenergia.conluz.infrastructure.admin.supply.consumption;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionService;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Controller for retrieving hourly consumption data for a specific supply
 */
@RestController
@RequestMapping("/api/v1/supplies/{supplyId}/consumption/hourly")
public class GetSupplyHourlyConsumptionController {

    private final GetDatadisConsumptionService getDatadisConsumptionService;

    public GetSupplyHourlyConsumptionController(GetDatadisConsumptionService getDatadisConsumptionService) {
        this.getDatadisConsumptionService = getDatadisConsumptionService;
    }

    @GetMapping
    @Operation(
            summary = "Retrieves hourly consumption data for a specific supply",
            description = """
                    This endpoint retrieves hourly consumption data from Datadis for a specific supply within a given date range.

                    **Authorization Rules:**
                    - Community Admins can retrieve consumption data for any supply they administer
                    - Supply owners can only retrieve consumption data for their own supplies

                    The consumption data includes:
                    - Energy taken from the grid
                    - Surplus energy (energy sent to grid)
                    - Self-consumption energy
                    - Obtain method (Real/Estimated)

                    """ + EnergyPeriodApiDocs.RANGE_BOUNDS + """

                    **Maximum length:** on this operation the limit of rule 5 is **31 calendar days**
                    instead of 24 calendar months: `endDate` must be no later than 31 calendar days after
                    `startDate`, counted in the period's time zone on the bounds as requested
                    (`ENERGY_METRICS_PERIOD_TOO_LONG`, with `params` `{"amount": "31", "unit": "DAYS"}`).

                    **Every hour:** one bucket is returned for every hour that starts inside the period,
                    in chronological order: 24 for a whole local day, 23 on the day the clocks go forward
                    and 25 on the day they go back.

                    **Hours without data:** Datadis publishes a month's self-consumption around the
                    10th of the following month; until then the supply's records carry consumption
                    but no self-consumption. An hour has data only when its record carries
                    self-consumption. An hour without data -- without self-consumption, or without
                    any record -- is returned with zero energy, a null `obtainMethod` and savings of
                    `0.00` rather than omitted, even when its consumption is already known, so that
                    no false self-consumption or savings is shown; its consumption appears once its
                    self-consumption is published. An hour whose published record carries no energy
                    has its `obtainMethod` set.

                    Every energy figure is in kWh, rounded to 3 decimals.

                    **`startsAt`:** the instant the hour starts at, with the offset in force in the
                    supply's time zone at that instant -- for example `2026-10-25T02:00:00+02:00` and
                    then `2026-10-25T02:00:00+01:00` for the two hours the clocks repeat in
                    Europe/Madrid. It is unique within the series and is the key to identify an hour by.

                    **`date` and `time`:** the local calendar date and local time, in the time zone the
                    application is configured with, at which each hour starts (`time` from `00:00` to
                    `23:00`). On the day the clocks go back, two consecutive hours carry the same `date`
                    and `time`; on the day they go forward, the local hour the clocks skip has no entry.

                    **Savings:** `savingsEur` is an **estimate** of what the hour's self-consumed energy
                    was worth, priced with the same tariff resolution as a day of the daily series, over
                    the hour itself; an `endDate` in the middle of an hour leaves a partial last hour,
                    priced over the part its energy covers. It prices the **energy term before taxes**
                    only: the power term, access tolls, charges and electricity tax are all excluded, and
                    VAT is applied only where the resolved tariff carries a rate. `tariffSource` says
                    where the prices came from -- `ESTIMATE` for a computed approximation, `REAL_TARIFF`
                    for the supply's contracted tariff. An hour whose published self-consumption is
                    zero, and an hour without data, reports `0.00`.
                    """,
            tags = ApiTag.SUPPLIES,
            operationId = "getSupplyHourlyConsumption",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Consumption data retrieved successfully",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canReadSupply(#supplyId)")
    public List<SupplyHourlyConsumptionBucketResponse> getSupplyHourlyConsumption(
            @PathVariable("supplyId") UUID supplyId,
            @Parameter(description = "Start of the period, inclusive.")
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @Parameter(description = "End of the period, exclusive.")
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate) {

        return getDatadisConsumptionService.getHourlySeriesBySupply(SupplyId.of(supplyId), startDate, endDate)
                .stream()
                .map(SupplyHourlyConsumptionBucketResponse::new)
                .toList();
    }
}
