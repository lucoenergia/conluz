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
                    - Total consumption in kWh
                    - Surplus energy (energy sent to grid)
                    - Self-consumption energy
                    - Obtain method (Real/Estimated)

                    Data is aggregated by month within the specified date range.

                    **Time zone:** each month covers the local calendar month of the time zone the
                    application is configured with, not the UTC one, so its total includes the first
                    and last local hours of the month and nothing from its neighbours.

                    **Range bounds:** `startDate` and `endDate` are both inclusive, and they select
                    pre-aggregated points by the instant each one is stamped at, which is local
                    midnight on the first day of its month. Pass them with the zone's offset to
                    select the months intended: local `2023-01-01T00:00:00+01:00` is
                    `2022-12-31T23:00:00Z`, so a bound expressed in UTC can select one month too few
                    or too many.
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
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate) {

        return getDatadisConsumptionService.getMonthlyConsumptionBySupply(SupplyId.of(supplyId), startDate, endDate)
                .stream()
                .map(SupplyConsumptionBucketResponse::new)
                .toList();
    }
}
