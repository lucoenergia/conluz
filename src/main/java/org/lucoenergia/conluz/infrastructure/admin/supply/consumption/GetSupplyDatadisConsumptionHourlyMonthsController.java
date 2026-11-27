package org.lucoenergia.conluz.infrastructure.admin.supply.consumption;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetSupplyDatadisConsumptionMonthsService;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/supplies/{supplyId}/consumption/datadis/hourly/months")
public class GetSupplyDatadisConsumptionHourlyMonthsController {

    private final GetSupplyDatadisConsumptionMonthsService service;

    public GetSupplyDatadisConsumptionHourlyMonthsController(GetSupplyDatadisConsumptionMonthsService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Lists the months holding a supply's hourly Datadis records, with how complete each one is.",
            description = """
                    Lists the calendar months that `getSupplyDatadisConsumptionHourlyCsv` can download with
                    content: one entry per month holding at least one of the supply's stored hourly Datadis
                    records.

                    **Months:** a month covers the hours that start from its first day at 00:00 up to, not
                    including, the first day of the next month at 00:00, in the time zone the application is
                    configured with, as for the CSV export. A month without any stored record is left out, so
                    months may be missing between two entries.

                    **Order and bounds:** newest month first. There is no lower or upper bound: every month with a
                    stored record is listed, however old. A supply without records is answered with an empty
                    array. A disabled supply still has every month listed.

                    **Counts:**
                    - `hoursWithData`: the number of stored records whose hour starts in the month. It is the
                      number of lines, after the header, that `getSupplyDatadisConsumptionHourlyCsv` writes for
                      the month, whatever fields each record carries.
                    - `expectedHours`: the number of hours of the whole month, accounting for daylight saving
                      transitions: a month in which the clocks go forward has one hour less, one in which they go
                      back one hour more. For the current month, it is the whole month, not the part elapsed so
                      far.

                    **Required: the caller can read the supply** (the supply's `canRead` capability), exactly
                    as for `getSupplyDatadisConsumptionHourlyCsv`: its owner, or an active community admin of
                    its community. Every other caller, a platform admin who is not a member included, is answered
                    404, and a request without a token 401.
                    """,
            tags = ApiTag.SUPPLIES,
            operationId = "getSupplyDatadisConsumptionHourlyMonths",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "The months holding the supply's hourly Datadis records, newest first",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canReadSupply(#supplyId)")
    public List<SupplyDatadisConsumptionMonthResponse> getSupplyDatadisConsumptionHourlyMonths(
            @PathVariable("supplyId") UUID supplyId) {

        return service.getHourlyMonths(SupplyId.of(supplyId)).stream()
                .map(SupplyDatadisConsumptionMonthResponse::new)
                .toList();
    }
}
