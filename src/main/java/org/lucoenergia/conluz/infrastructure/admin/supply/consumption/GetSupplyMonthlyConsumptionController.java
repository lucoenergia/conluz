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
 * Controller for retrieving monthly consumption data for a specific supply
 */
@RestController
@RequestMapping("/api/v1/supplies/{supplyId}/consumption/monthly")
public class GetSupplyMonthlyConsumptionController {

    private final GetDatadisConsumptionService getDatadisConsumptionService;

    public GetSupplyMonthlyConsumptionController(GetDatadisConsumptionService getDatadisConsumptionService) {
        this.getDatadisConsumptionService = getDatadisConsumptionService;
    }

    @GetMapping
    @Operation(
            summary = "Retrieves monthly consumption data for a specific supply",
            description = """
                    This endpoint retrieves monthly consumption data from Datadis for a specific supply within a given date range.

                    **Authorization Rules:**
                    - Community Admins can retrieve consumption data for any supply they administer
                    - Supply owners can only retrieve consumption data for their own supplies

                    The consumption data includes:
                    - Energy taken from the grid
                    - Surplus energy (energy fed back to the grid)
                    - Self-consumption energy
                    - Obtain method (Real/Estimated)

                    Data is aggregated by month within the specified date range. Every energy figure
                    is in kWh, rounded to 3 decimals.

                    **Source:** each month is summed from the supply's hourly records when the series
                    is read, so it is as fresh as the energy metrics.

                    **Months with data only:** Datadis publishes a month's self-consumption around the
                    10th of the following month; until then the supply's records carry consumption
                    but no self-consumption. A month is returned only when the records carrying
                    self-consumption cover at least 90 % of the month's hours inside the period. A
                    month below that is omitted, as is a month without any record, so an unpublished
                    month never reads as zero self-consumption.

                    **Time zone:** each month covers the local calendar month of the time zone the
                    application is configured with, not the UTC one, so its total includes the first
                    and last local hours of the month and nothing from its neighbours.

                    """ + EnergyPeriodApiDocs.RANGE_BOUNDS + """

                    A month is selected if and only if its local midnight on the first day falls
                    inside the half-open bounds. Pass them with the zone's offset to select the months
                    intended: local `2023-01-01T00:00:00+01:00` is `2022-12-31T23:00:00Z`, so a bound
                    expressed in UTC can select one month too few or too many.

                    **Months cut by the end bound.** A month the end bound falls inside covers only
                    its hours before that bound, energy and savings alike: a request ending on the
                    15th returns that month's first half.

                    **Savings:** `savingsEur` is an **estimate** of what the bucket's self-consumed
                    energy was worth. It prices the **energy term before taxes** only: the power
                    term, access tolls, charges and electricity tax are all excluded, and VAT is
                    applied only where the resolved tariff carries a rate. `tariffSource` says where
                    the prices came from -- `ESTIMATE` for a computed approximation, `REAL_TARIFF`
                    for the supply's contracted tariff -- and a single estimated stretch of the
                    bucket makes the whole amount an estimate. A month whose published
                    self-consumption is zero reports `0.00`.
                    """,
            tags = ApiTag.SUPPLIES,
            operationId = "getSupplyMonthlyConsumption",
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
    public List<SupplyConsumptionBucketResponse> getSupplyMonthlyConsumption(
            @PathVariable("supplyId") UUID supplyId,
            @Parameter(description = "Start of the period, inclusive.")
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @Parameter(description = "End of the period, exclusive.")
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate) {

        return getDatadisConsumptionService.getMonthlySeriesBySupply(SupplyId.of(supplyId), startDate, endDate)
                .stream()
                .map(SupplyConsumptionBucketResponse::new)
                .toList();
    }
}
