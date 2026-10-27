package org.lucoenergia.conluz.infrastructure.admin.supply.consumption;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsReferencePeriod;
import org.lucoenergia.conluz.domain.consumption.GetSupplyEnergyMetricsService;
import org.lucoenergia.conluz.domain.consumption.SupplyEnergyMetrics;
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
import java.util.UUID;

/**
 * Controller for retrieving aggregated energy metrics for a specific supply
 */
@RestController
@RequestMapping("/api/v1/supplies/{supplyId}/energy-metrics")
public class GetSupplyEnergyMetricsController {

    private final GetSupplyEnergyMetricsService getSupplyEnergyMetricsService;

    public GetSupplyEnergyMetricsController(GetSupplyEnergyMetricsService getSupplyEnergyMetricsService) {
        this.getSupplyEnergyMetricsService = getSupplyEnergyMetricsService;
    }

    @GetMapping
    @Operation(
            summary = "Retrieves aggregated energy metrics for a specific supply",
            description = """
                    This endpoint aggregates the consumption data stored for a specific supply into energy
                    totals and two ratios, on a 0-1 scale:

                    - `selfSufficiencyRatio`: self-consumed energy divided by total consumed energy.
                    - `selfConsumptionRatio`: self-consumed energy divided by the energy assigned to the supply.

                    Both ratios are computed by summing every record first and dividing once, so hours of
                    different magnitude weigh differently. A ratio whose denominator is zero is returned as
                    `null`, never as `0`.

                    **Authorization Rules:**
                    - Community Admins can retrieve energy metrics for any supply they administer
                    - Supply owners can only retrieve energy metrics for their own supplies

                    **Period:**
                    The period is resolved in exactly one of three ways, and the resolved bounds are
                    always reported in `period`, also when the caller supplied them.

                    - `startDate` and `endDate`: an explicit period. They must be supplied together;
                      supplying exactly one of them is a bad request, as is a `startDate` that is not
                      before the `endDate`.
                    - `period=LATEST_PUBLISHED_MONTH`: the most recent complete calendar month, in the
                      supply's time zone, that the distributor has published for **this supply**. It is
                      resolved from this supply's records alone: a month published only for another supply
                      of the same owner is never chosen. An hourly record is published when it carries
                      self-consumed energy, zero included, and surplus alone does not count. A month is
                      published when at least 90% of the hours it could have published carry it: the hours
                      of the month from the later of its first hour and the supply's first published
                      record. An unpublished or partly published month is skipped and the search continues
                      backwards through the 24 complete months before the current one. The current month is
                      never a candidate. The period runs from `00:00` of the first day of the month to
                      `00:00` of the first day of the next month. Combining it with `startDate` or
                      `endDate` is a bad request.
                    - Neither: from the supply's earliest stored record to the end of the hour of its
                      latest.

                    When no period can be resolved -- no published month in the search window, or no
                    stored record at all -- the response is still successful, with null period bounds,
                    zero totals and null ratios.

                    """ + EnergyPeriodApiDocs.RANGE_BOUNDS + """

                    The `coverage` object reports how many hourly records were found against how many hours
                    the period spans, so a partially synchronised period can be told apart from a genuinely
                    low ratio. Hours without a record are left out of the sums; they are never counted as
                    zero.

                    **Savings:**
                    `savings.amountEur` is an **estimate** of what the self-consumed energy of the period
                    was worth. It prices the **energy term before taxes** only: the power term, access
                    tolls, charges and electricity tax are all excluded, and VAT is applied only where the
                    resolved tariff carries a rate. `savings.tariffSource` says where the prices came from
                    -- `ESTIMATE` for a computed approximation, `REAL_TARIFF` for the supply's contracted
                    tariff -- and a single estimated stretch of the period makes the whole amount an
                    estimate.

                    The amount **does not distinguish missing data from genuine zeros**: an hour with no
                    stored record contributes nothing, exactly as it contributes nothing to the energy
                    totals, so a partially synchronised period yields a proportionally low figure rather
                    than a flagged one. `coverage` is the field that tells the two apart. Consistently
                    with that, an explicitly requested period containing no record at all is worth `0.00`,
                    while `null` is reserved for the case where no period could be resolved: a supply
                    with no stored record and no requested period, or no published month for
                    `period=LATEST_PUBLISHED_MONTH`.
                    """,
            tags = ApiTag.SUPPLIES,
            operationId = "getSupplyEnergyMetrics",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Energy metrics retrieved successfully",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canReadSupply(#supplyId)")
    public SupplyEnergyMetricsResponse getSupplyEnergyMetrics(
            @PathVariable("supplyId") UUID supplyId,
            @Parameter(description = "Start of an explicit period, inclusive. Requires endDate.")
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @Parameter(description = "End of an explicit period, exclusive. Requires startDate.")
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @Parameter(description = "A period the server resolves by itself. Cannot be combined with " +
                    "startDate or endDate.")
            @RequestParam(value = "period", required = false) EnergyMetricsReferencePeriod period) {

        SupplyEnergyMetrics metrics = getSupplyEnergyMetricsService.getEnergyMetrics(
                SupplyId.of(supplyId), startDate, endDate, period);

        return new SupplyEnergyMetricsResponse(metrics);
    }
}
