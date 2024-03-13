package org.lucoenergia.conluz.infrastructure.production;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.apache.commons.lang3.StringUtils;
import org.lucoenergia.conluz.domain.production.GetProductionService;
import org.lucoenergia.conluz.domain.production.ProductionByTime;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/production/monthly")
public class GetMonthlyProductionController {

    private final GetProductionService getProductionService;

    public GetMonthlyProductionController(GetProductionService getProductionService) {
        this.getProductionService = getProductionService;
    }

    @GetMapping
    @Operation(
            summary = "Retrieves monthly energy production data for a specified power plant supply within a given date interval.",
            description = "This endpoint enables users to retrieve monthly energy production data from a specific power plant supply, identified by its unique supply ID, within a specified date interval. Clients can include query parameters to define the start and end dates, providing flexibility in customizing the data retrieval. Proper authentication, through an authentication token, is required for secure access. A successful request returns an HTTP status code of 200, delivering a dataset that includes monthly energy production metrics for each day within the specified interval for the specified power plant supply. In cases of errors or invalid parameters, the server responds with an appropriate error status code accompanied by a descriptive message. This endpoint is valuable for monitoring and analyzing the monthly energy output of a specific power plant supply, facilitating performance assessment and optimization based on the provided date range.",
            tags = ApiTag.PRODUCTION,
            operationId = "getMonthlyProduction"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Query executed successfully",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(
                                    value = """
                                            
                                            """
                            )
                    )
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    public List<ProductionByTime> getMonthlyProduction(
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @RequestParam(required = false) UUID supplyId) {

        if (Objects.isNull(supplyId)) {
            return getProductionService.getMonthlyProductionByRangeOfDates(startDate, endDate);
        }
        return getProductionService.getMonthlyProductionByRangeOfDatesAndSupply(startDate, endDate,
                SupplyId.of(supplyId));
    }
}
