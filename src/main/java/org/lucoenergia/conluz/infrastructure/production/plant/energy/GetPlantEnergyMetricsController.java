package org.lucoenergia.conluz.infrastructure.production.plant.energy;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.production.plant.energy.GetPlantEnergyMetricsService;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergyMetrics;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergyMetricsReferencePeriod;
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
import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/plants/{plantId}/energy-metrics",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class GetPlantEnergyMetricsController {

    private final GetPlantEnergyMetricsService service;

    public GetPlantEnergyMetricsController(GetPlantEnergyMetricsService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Retrieves the energy metrics of a plant: its production as reported by the distributor.",
            description = """
                    Aggregates the distributor's hourly records of the plant's own supply over one period.
                    No code or identifier of that supply is reported.

                    - `energy.productionKWh`: what the plant produced, as reported by the distributor for
                      the plant's own supply: the energy the plant exported to the grid. Totalled from the
                      hourly records, not from the monthly pre-aggregates. An hour without a record adds
                      nothing; an hour stored as 0, such as at night, is a measured zero.
                    - `coverage.hoursWithData` and `coverage.expectedHours`: how many hourly records were
                      found in the period, zeros included, and how many hours the period spans.

                    A figure nothing was stored for is `null`, never `0`: a period without any production
                    record has null production.

                    **Period:**
                    The period is resolved in exactly one of three ways, and the resolved bounds are
                    always reported in `period`, also when the caller supplied them, with an end after
                    the current hour clipped as described under **Period checks**.

                    - `startDate` and `endDate`: an explicit period. They must be supplied together, and
                      a `startDate` that is not before the `endDate` is a bad request.
                    - `period=LATEST_PRODUCTION_DAY`: the latest complete local day, in the community's
                      time zone, with production: the latest day whose stored hourly records cover all
                      its hours (23, 24 or 25 on the days the clocks change). The search goes backwards
                      through the 24 months before the current day; the current day is never a
                      candidate, even when all its hours are stored. When no day in the search window is
                      complete, no period is resolved. Combining it with `startDate` or `endDate` is a
                      bad request.
                    - Neither: from the earliest production record stored for the plant to the end of the
                      hour of the latest.

                    When no period can be resolved, the response is still successful, with null period
                    bounds, null production and no hour.

                    """ + EnergyPeriodApiDocs.RANGE_BOUNDS + """

                    **Required: the caller can manage the plant** (the plant's `canManage` capability):
                    an active community admin of the plant's community. A member of that community who is
                    not an admin is answered 403, and every other caller, a platform admin who is not a
                    member included, 404.
                    """,
            tags = ApiTag.PLANTS,
            operationId = "getPlantEnergyMetrics",
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
    @PreAuthorize("@communityAccessGuard.canManagePlant(#plantId)")
    public PlantEnergyMetricsResponse getPlantEnergyMetrics(
            @PathVariable("plantId") UUID plantId,
            @Parameter(description = "Start of an explicit period, inclusive. Requires endDate.")
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @Parameter(description = "End of an explicit period, exclusive. Requires startDate.")
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @Parameter(description = "A period the server resolves by itself. Cannot be combined with " +
                    "startDate or endDate.")
            @RequestParam(value = "period", required = false) PlantEnergyMetricsReferencePeriod period) {

        PlantEnergyMetrics metrics = service.getEnergyMetrics(plantId, startDate, endDate, period);

        return new PlantEnergyMetricsResponse(metrics);
    }
}
