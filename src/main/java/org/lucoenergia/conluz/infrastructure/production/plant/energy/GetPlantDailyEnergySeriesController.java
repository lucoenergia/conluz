package org.lucoenergia.conluz.infrastructure.production.plant.energy;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.production.plant.energy.GetPlantDailyEnergySeriesService;
import org.lucoenergia.conluz.infrastructure.admin.supply.consumption.EnergyPeriodApiDocs;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/plants/{plantId}/energy-series/daily",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class GetPlantDailyEnergySeriesController {

    private final GetPlantDailyEnergySeriesService service;

    public GetPlantDailyEnergySeriesController(GetPlantDailyEnergySeriesService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Retrieves the production of a plant as reported by the distributor, per day.",
            description = """
                    For each day of the requested period, what the plant produced, as reported by the
                    distributor for the plant's own supply: the energy the plant exported to the grid, its
                    hourly records added up. No code or identifier of that supply is reported. Each
                    figure sums the unrounded records and is rounded once, to 3 decimals, on the result.

                    """ + EnergyPeriodApiDocs.RANGE_BOUNDS + """

                    **Every day is emitted:**
                    A bucket is returned for every local day of the community's time zone that overlaps
                    the period, in chronological order, whether or not the plant has a record that day. A
                    day the period starts or ends inside counts only its part inside the period.

                    **Completeness:**
                    `expectedHours` is the number of the day's hours inside the period, and
                    `hoursWithData` how many of them have a stored record. A day with `hoursWithData`
                    below `expectedHours` is incomplete.

                    **Gaps and measured zeros:**
                    A day without any stored record has `null` production and an `hoursWithData` of 0,
                    never a zero; an hour stored as 0, such as at night, is a measured zero, `0`, and
                    counts as a record.

                    **Required: the caller can manage the plant** (the plant's `canManage` capability):
                    an active community admin of the plant's community. A member of that community who is
                    not an admin is answered 403, and every other caller, a platform admin who is not a
                    member included, 404.
                    """,
            tags = ApiTag.PLANTS,
            operationId = "getPlantDailyEnergySeries",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Daily energy series retrieved successfully",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canManagePlant(#plantId)")
    public List<PlantDailyEnergyBucketResponse> getPlantDailyEnergySeries(
            @PathVariable("plantId") UUID plantId,
            @Parameter(description = "Start of the period, inclusive.")
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @Parameter(description = "End of the period, exclusive.")
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate) {

        return service.getDailySeries(plantId, startDate, endDate)
                .stream()
                .map(PlantDailyEnergyBucketResponse::new)
                .toList();
    }
}
