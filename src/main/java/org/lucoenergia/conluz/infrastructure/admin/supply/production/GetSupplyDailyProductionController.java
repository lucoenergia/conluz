package org.lucoenergia.conluz.infrastructure.admin.supply.production;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.lucoenergia.conluz.domain.production.ProductionByTime;
import org.lucoenergia.conluz.domain.production.get.GetProductionService;
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
 * Controller for retrieving daily production data for a specific supply
 */
@RestController
@RequestMapping("/api/v1/supplies/{id}/production/daily")
public class GetSupplyDailyProductionController {

    private final GetProductionService getProductionService;

    public GetSupplyDailyProductionController(GetProductionService getProductionService) {
        this.getProductionService = getProductionService;
    }

    @GetMapping
    @Operation(
            summary = "Retrieves daily production data assigned to a specific supply",
            description = "This endpoint retrieves daily energy production data assigned to a specific supply point within a given date interval. The production values are calculated by multiplying the total production by the supply's partition coefficient. This endpoint is useful for tracking the energy production allocated to individual supply points in the energy community.",
            tags = ApiTag.SUPPLIES,
            operationId = "getSupplyDailyProduction"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Query executed successfully",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("isAuthenticated()")
    public List<ProductionByTime> getSupplyDailyProduction(
            @PathVariable("id") UUID id,
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate) {

        return getProductionService.getDailyProductionByRangeOfDatesAndSupply(startDate, endDate, SupplyId.of(id));
    }
}
