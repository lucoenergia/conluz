package org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.GetPlantSurplusClaimService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/plants/{plantId}/surplus-claim",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class GetPlantSurplusClaimController {

    private final GetPlantSurplusClaimService service;

    public GetPlantSurplusClaimController(GetPlantSurplusClaimService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Previews the surplus a plant would claim from its retailer for one closed month, hour by hour.",
            description = """
                    Builds, for one closed month, the plant's surplus hour by hour as the community admin would
                    claim it from the retailer, with what is missing from it. Nothing is stored: it is computed
                    on every request. Days and hours are read in the community's time zone.

                    **Supplies in scope:** the supplies holding an applied coefficient of the plant (one the
                    distributor applies) whose period overlaps the month, the plant's own supply excluded. Their
                    surplus is summed over the whole month.

                    - `days[].hours[]`: every hour of every local day, 23, 24 or 25 on the days the clocks
                      change, each identified by the instant it starts at. An hour's surplus is the sum of the
                      records of the supplies in scope; it is `null` when none of them stored a surplus record
                      for that hour, and `0` when the records carry zero.
                    - `days[].state`: `REAL` when every supply in scope stored a surplus record for every hour of
                      the day, `ESTIMATED` otherwise, and on every day when no supply is in scope.
                    - `days[].peakHour`: the hour with the day's largest surplus, the earliest on a tie; `null`
                      when the day's surplus is `null` or `0`.
                    - `summary.surplusIncome`: an **estimate** of what the month's surplus earned at OMIE
                      day-ahead prices, computed as for the community energy metrics' surplus income, over
                      exactly the month's hourly surplus; `null` when `summary.surplusKWh` is.

                    Energy is rounded to 3 decimals and euros to cents, once, on the unrounded sums.

                    **Month:** `month` names a closed calendar month. Without it, the latest month whose
                    assigned production Datadis has published for the plant's supplies
                    (`availableMonths.latestPublishedMonth`), else the last closed month. The current month
                    and later months are a bad request with code `SURPLUS_CLAIM_MONTH_NOT_CLOSED`. A closed
                    month outside `availableMonths` is answered normally; its warnings say why it is empty.

                    **Warnings** never prevent the claim; a list is empty, never `null`, when its warning is
                    not raised. They name the supplies concerned, with their CUPS, because the admin manages
                    them.

                    **Required: the caller can manage the plant** (the plant's `canManage` capability): an
                    active community admin of the plant's community. A member of that community who is not an
                    admin is answered 403, and every other caller, a platform admin who is not a member
                    included, 404.
                    """,
            tags = ApiTag.PLANTS,
            operationId = "getPlantSurplusClaim",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Surplus claim preview computed successfully",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canManagePlant(#plantId)")
    public PlantSurplusClaimResponse getPlantSurplusClaim(
            @PathVariable("plantId") UUID plantId,
            @Parameter(description = "The closed calendar month to claim, as YYYY-MM. Defaults to the latest " +
                    "published month, else the last closed month.",
                    schema = @Schema(type = "string", pattern = "^\\d{4}-(0[1-9]|1[0-2])$", example = "2026-09"))
            @RequestParam(value = "month", required = false)
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {

        return new PlantSurplusClaimResponse(service.getSurplusClaim(plantId, month));
    }
}
