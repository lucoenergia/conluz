package org.lucoenergia.conluz.infrastructure.production.plant.inverter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.production.plant.inverter.GetPlantInverterProductionTodayService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/plants/{plantId}/inverter-production/today",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class GetPlantInverterProductionTodayController {

    private final GetPlantInverterProductionTodayService service;

    public GetPlantInverterProductionTodayController(GetPlantInverterProductionTodayService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Retrieves today's production of a plant as reported by its inverter.",
            description = """
                    Derives today's energy and a power curve from the real-time snapshots the plant's Huawei
                    inverter reports about every 5 minutes. "Today" is the current local day in the community's
                    time zone. No station code, provider code or credential of the integration is reported.

                    This is inverter data: the gross output the inverter measures. It may differ from the
                    distributor's figures, which measure the energy exported at the connection point, and it is
                    never summed with the energy metrics or energy series figures.

                    - `status`: `NOT_AVAILABLE` when the plant has no Huawei configuration or it is disabled,
                      `NO_DATA_TODAY` when it is enabled but no snapshot of today is stored, and `AVAILABLE`
                      otherwise. Every other field is `null` unless the status is `AVAILABLE`.
                    - `todayEnergyKWh`: the latest inverter day counter of today. The counter resets at local
                      midnight, but a snapshot taken at the boundary can still carry the previous day's total:
                      the leading run of today's snapshots equal to the previous day's last value, when that is
                      greater than 0, is carry-over and counts as 0 kWh. The run ends at the first different
                      value. When every snapshot of today is carry-over, today's energy is 0.
                    - `powerCurve`: one point per pair of consecutive snapshots of today, at the later one: the
                      energy counted between them divided by their real time apart, never negative. An interval
                      longer than 15 minutes is a gap: its point is `null`, and nothing is interpolated across
                      it. A single missing snapshot, about 10 minutes, is averaged.
                    - `lastUpdatedAt`: when the latest snapshot of today was taken.
                    - `stale`: whether that snapshot is more than 60 minutes old.

                    Energy and power are rounded to 3 decimals, after computing.

                    **Required: the caller can manage the plant** (the plant's `canManage` capability):
                    an active community admin of the plant's community. A member of that community who is
                    not an admin is answered 403, and every other caller, a platform admin who is not a
                    member included, 404.
                    """,
            tags = ApiTag.PLANTS,
            operationId = "getPlantInverterProductionToday",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Today's inverter production retrieved successfully",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canManagePlant(#plantId)")
    public PlantInverterProductionTodayResponse getPlantInverterProductionToday(
            @PathVariable("plantId") UUID plantId) {

        return new PlantInverterProductionTodayResponse(service.getToday(plantId));
    }
}
