package org.lucoenergia.conluz.infrastructure.production.get;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.access.CommunityAccessGuard;
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
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/communities/{communityId}/production/monthly")
public class GetMonthlyProductionController {

    private final GetProductionService getProductionService;
    private final CommunityAccessGuard communityAccessGuard;

    public GetMonthlyProductionController(GetProductionService getProductionService,
                                          CommunityAccessGuard communityAccessGuard) {
        this.getProductionService = getProductionService;
        this.communityAccessGuard = communityAccessGuard;
    }

    @GetMapping
    @Operation(
            summary = "Retrieves monthly energy production data of a community within a given date interval.",
            description = "Retrieves monthly energy production data for the plants of the community identified by the path "
                    + "`communityId`, within the specified date interval. **Required: any member of the community.** "
                    + "Returns 404 if the community does not exist or the caller is not a member of it. When a `supplyId` "
                    + "is provided, only the supply owner or a Community Admin of the supply's community may access it, "
                    + "and the supply must belong to the community in the path.",
            tags = ApiTag.PRODUCTION,
            operationId = "getMonthlyProduction"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Query executed successfully",
                    useReturnTypeSchema = true
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @NotFoundErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("isAuthenticated() and (#supplyId == null or @communityAccessGuard.canReadSupply(#supplyId))")
    public List<ProductionByTime> getMonthlyProduction(
            @PathVariable UUID communityId,
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @RequestParam(required = false) UUID supplyId) {

        if (!communityAccessGuard.canReadCommunity(communityId)) {
            throw new CommunityNotFoundException(communityId);
        }
        if (Objects.isNull(supplyId)) {
            return getProductionService.getMonthlyProductionByRangeOfDatesAndCommunity(startDate, endDate, communityId);
        }
        return getProductionService.getMonthlyProductionByRangeOfDatesAndCommunityAndSupply(startDate, endDate,
                communityId, SupplyId.of(supplyId));
    }
}
