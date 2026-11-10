package org.lucoenergia.conluz.infrastructure.production.plant.energy;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.production.plant.energy.GetPlantHourlyEnergySeriesService;
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
        value = "/api/v1/plants/{plantId}/energy-series/hourly",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class GetPlantHourlyEnergySeriesController {

    private final GetPlantHourlyEnergySeriesService service;

    public GetPlantHourlyEnergySeriesController(GetPlantHourlyEnergySeriesService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Retrieves the production of a plant as reported by the distributor, per hour.",
            description = """
                    For each hour of the requested period, what the plant produced, as reported by the
                    distributor for the plant's own supply: the energy the plant exported to the grid, its
                    hourly records added up. No code or identifier of that supply is reported. Each
                    figure sums the unrounded records and is rounded once, to 3 decimals, on the result.

                    """ + EnergyPeriodApiDocs.RANGE_BOUNDS + """

                    **Maximum length:** on this operation the limit of rule 5 is **31 calendar days**
                    instead of 24 calendar months: `endDate` must be no later than 31 calendar days after
                    `startDate`, counted in the period's time zone on the bounds as requested
                    (`ENERGY_METRICS_PERIOD_TOO_LONG`, with `params` `{"amount": "31", "unit": "DAYS"}`).

                    **Every hour is emitted:**
                    A bucket is returned for every whole hour that starts inside the period, in
                    chronological order, whether or not the plant has a record that hour: 24 for a whole
                    local day of the community's time zone, 23 on the day the clocks go forward and 25
                    on the day they go back. `startsAt` is the instant the hour starts at, with the
                    offset in force in the community's time zone, and is unique within the series;
                    `date` and `time` are the local date and time the hour starts at, which two
                    consecutive hours share on the day the clocks go back.

                    **Completeness:**
                    `expectedHours` is the number of the hour's hours inside the period, and
                    `hoursWithData` how many of them have a stored record. A hour with `hoursWithData`
                    below `expectedHours` is incomplete.

                    **Gaps and measured zeros:**
                    A hour without any stored record has `null` production and an `hoursWithData` of 0,
                    never a zero; an hour stored as 0, such as at night, is a measured zero, `0`, and
                    counts as a record.

                    **Required: the caller can manage the plant** (the plant's `canManage` capability):
                    an active community admin of the plant's community. A member of that community who is
                    not an admin is answered 403, and every other caller, a platform admin who is not a
                    member included, 404.
                    """,
            tags = ApiTag.PLANTS,
            operationId = "getPlantHourlyEnergySeries",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Hourly energy series retrieved successfully",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canManagePlant(#plantId)")
    public List<PlantHourlyEnergyBucketResponse> getPlantHourlyEnergySeries(
            @PathVariable("plantId") UUID plantId,
            @Parameter(description = "Start of the period, inclusive.")
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @Parameter(description = "End of the period, exclusive.")
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate) {

        return service.getHourlySeries(plantId, startDate, endDate)
                .stream()
                .map(PlantHourlyEnergyBucketResponse::new)
                .toList();
    }
}
