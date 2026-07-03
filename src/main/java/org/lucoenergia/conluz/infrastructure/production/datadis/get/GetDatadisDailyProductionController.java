package org.lucoenergia.conluz.infrastructure.production.datadis.get;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.production.datadis.DatadisProduction;
import org.lucoenergia.conluz.domain.production.datadis.get.GetDatadisProductionService;
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
 * Retrieves daily Datadis production data for a community.
 *
 * <p>See {@link GetDatadisHourlyProductionController} for the intentional per-community (not
 * per-supply) authorization asymmetry with consumption and the {@code isMemberOfCommunity} rationale.
 */
@RestController
@RequestMapping("/api/v1/communities/{communityId}/production/datadis/daily")
public class GetDatadisDailyProductionController {

    private final GetDatadisProductionService getDatadisProductionService;

    public GetDatadisDailyProductionController(GetDatadisProductionService getDatadisProductionService) {
        this.getDatadisProductionService = getDatadisProductionService;
    }

    @GetMapping
    @Operation(
            summary = "Retrieves daily Datadis production data of a community within a given date interval.",
            description = """
                    Retrieves daily Datadis-derived production data for the plants of the community identified by
                    `communityId`, within the specified date range. **Required: any member of the community.**

                    Returns 404 if the community does not exist or the caller is not a member of it. When a
                    `supplyId` is provided, it must back a plant of the community in the path; otherwise a 404 is
                    returned (an out-of-community supply is never confirmed to exist).

                    Data is aggregated by day within the specified date range.
                    """,
            tags = ApiTag.PRODUCTION,
            operationId = "getDatadisDailyProduction",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Production data retrieved successfully",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("isAuthenticated() and @communityAccessGuard.isMemberOfCommunity(#communityId)")
    public List<DatadisProduction> getDatadisDailyProduction(
            @PathVariable UUID communityId,
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @RequestParam(required = false) UUID supplyId) {

        return getDatadisProductionService.getDailyProduction(communityId,
                supplyId == null ? null : SupplyId.of(supplyId), startDate, endDate);
    }
}
