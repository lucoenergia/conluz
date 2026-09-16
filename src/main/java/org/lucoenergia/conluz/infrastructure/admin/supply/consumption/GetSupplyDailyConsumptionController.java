package org.lucoenergia.conluz.infrastructure.admin.supply.consumption;

import io.swagger.v3.oas.annotations.Operation;
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
 * Controller for retrieving daily consumption data for a specific supply
 */
@RestController
@RequestMapping("/api/v1/supplies/{supplyId}/consumption/daily")
public class GetSupplyDailyConsumptionController {

    private final GetDatadisConsumptionService getDatadisConsumptionService;

    public GetSupplyDailyConsumptionController(GetDatadisConsumptionService getDatadisConsumptionService) {
        this.getDatadisConsumptionService = getDatadisConsumptionService;
    }

    @GetMapping
    @Operation(
            summary = "Retrieves daily consumption data for a specific supply",
            description = """
                    This endpoint retrieves daily consumption data from Datadis for a specific supply within a given date range.

                    **Authorization Rules:**
                    - Community Admins can retrieve consumption data for any supply they administer
                    - Supply owners can only retrieve consumption data for their own supplies

                    The consumption data includes:
                    - Total consumption in kWh
                    - Surplus energy (energy sent to grid)
                    - Self-consumption energy
                    - Obtain method (Real/Estimated)

                    Data is aggregated by day within the specified date range.

                    **Time zone:** days follow the local calendar of the time zone the application is
                    configured with, not UTC. A day therefore lasts 23 hours on the spring daylight
                    saving transition and 25 hours on the autumn one.

                    **Range bounds:** `startDate` and `endDate` are both inclusive and are taken as
                    given, without being rounded to a day boundary. Bounds that fall in the middle of
                    a local day produce a partial first and last bucket, so a caller wanting whole
                    local days must pass them at local midnight and at 23:59:59 local time, offset
                    included -- for example `2023-04-01T00:00:00+02:00` to
                    `2023-04-30T23:59:59+02:00` for April 2023 in Europe/Madrid. A day with no stored
                    record is returned with zero energy rather than omitted.

                    Each day is priced over its own local day, clipped to the requested bounds, so a
                    partial edge bucket's savings cover exactly the hours its energy fields cover.

                    **Savings:** `savingsEur` is an **estimate** of what the bucket's self-consumed
                    energy was worth. It prices the **energy term before taxes** only: the power
                    term, access tolls, charges and electricity tax are all excluded, and VAT is
                    applied only where the resolved tariff carries a rate. `tariffSource` says where
                    the prices came from -- `ESTIMATE` for a computed approximation, `REAL_TARIFF`
                    for the supply's contracted tariff -- and a single estimated stretch of the
                    bucket makes the whole amount an estimate. A bucket with no self-consumption,
                    and a bucket with no stored record at all, reports `0.00`.

                    Datadis publishes a month's self-consumption around the 10th of the following
                    month, so the current month has no self-consumption data yet and its savings
                    come back as zero.
                    """,
            tags = ApiTag.SUPPLIES,
            operationId = "getSupplyDailyConsumption",
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
    public List<SupplyConsumptionBucketResponse> getSupplyDailyConsumption(
            @PathVariable("supplyId") UUID supplyId,
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate) {

        return getDatadisConsumptionService.getDailySeriesBySupply(SupplyId.of(supplyId), startDate, endDate)
                .stream()
                .map(SupplyConsumptionBucketResponse::new)
                .toList();
    }
}
