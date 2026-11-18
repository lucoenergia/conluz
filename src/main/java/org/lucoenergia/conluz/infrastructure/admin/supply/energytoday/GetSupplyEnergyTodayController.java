package org.lucoenergia.conluz.infrastructure.admin.supply.energytoday;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.supply.energytoday.GetSupplyEnergyTodayService;
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
        value = "/api/v1/supplies/{supplyId}/energy-today",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class GetSupplyEnergyTodayController {

    private final GetSupplyEnergyTodayService service;

    public GetSupplyEnergyTodayController(GetSupplyEnergyTodayService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Retrieves today's production assigned to a supply point.",
            description = """
                    Derives the share of today's production assigned to the supply point from the real-time
                    snapshots the Huawei inverters of its plants report about every 5 minutes, and from the
                    supply's partition coefficients. "Today" is the current local day in the community's time
                    zone, from local midnight. The plants used are those where the supply has a partition
                    coefficient in force at some instant of today so far, an enabled Huawei configuration and
                    at least one snapshot of today. No plant, station code, provider code or credential is
                    reported.

                    This is derived from inverter data: the gross output the inverter measures. It may differ
                    from the distributor's figures and is never summed with the energy metrics or energy series
                    figures.

                    - `production.status`, the first that applies: `NO_COEFFICIENT_TODAY` when the supply has
                      no partition coefficient in force at any instant of today so far, in any plant;
                      `NOT_AVAILABLE` when none of those plants has an enabled Huawei configuration;
                      `NO_DATA_TODAY` when at least one has, but none has a snapshot of today; and `AVAILABLE`
                      otherwise. Every other field of `production` is `null` unless the status is `AVAILABLE`.
                    - Intervals: per plant, one interval from local midnight to the first snapshot of today,
                      then one per pair of consecutive snapshots. Each interval's energy is counted from the
                      inverter day counter, with the same carry-over rule as `getPlantInverterProductionToday`,
                      and multiplied by the supply's partition coefficient in that plant in force at the
                      interval's start, 0 when none is.
                    - `assignedEnergyKWh`: the sum over every interval of every plant used. Intervals longer
                      than 15 minutes, the gaps of the curve, are included.
                    - `assignedPowerCurve`: one point per fixed 5-minute slot from local midnight, at the slot's
                      end, for every slot ending at or before `lastUpdatedAt`: the assigned energy of the slot
                      divided by its 5 minutes, in kW. An interval's energy is split over the slots it overlaps
                      in proportion to the time overlapped. A slot is `null` when an interval longer than 15
                      minutes overlaps it, or when the snapshots of a plant used do not cover all of it.
                    - With several plants, energy and power are the sums over them.
                    - `currentAssignedPowerKW` is the value of the last point of the curve, and
                      `currentPlantPowerKW` the power of the plants used in that same slot, before applying
                      any partition coefficient.
                    - `lastUpdatedAt`: the latest snapshot of today across the plants used. `stale`: whether it
                      is more than 15 minutes old; one exactly 15 minutes old is not stale.

                    Energy and power are rounded to 3 decimals, after computing.

                    **Required: the caller can read the supply** (the supply's `canRead` capability): its owner,
                    while their membership in the supply's community is active, or an active community admin
                    of that community. Every other caller, a member of the same community, a member of another
                    community and a platform admin who is not a member included, is answered 404, as is a
                    supply that does not exist.
                    """,
            tags = ApiTag.SUPPLIES,
            operationId = "getSupplyEnergyToday",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Today's assigned production retrieved successfully",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canReadSupply(#supplyId)")
    public SupplyEnergyTodayResponse getSupplyEnergyToday(@PathVariable("supplyId") UUID supplyId) {

        return new SupplyEnergyTodayResponse(service.getToday(supplyId));
    }
}
