package org.lucoenergia.conluz.infrastructure.price.get;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.lucoenergia.conluz.domain.price.get.GetPriceService;
import org.lucoenergia.conluz.domain.price.PriceByHour;
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

@RestController
@RequestMapping("/api/v1/prices")
public class GetPriceByHourController {

    private final GetPriceService getPriceService;

    public GetPriceByHourController(GetPriceService getPriceService) {
        this.getPriceService = getPriceService;
    }

    @GetMapping
    @Operation(
            summary = "Retrieve hourly energy prices within a specified date interval.",
            description = "This endpoint enables users to access detailed hourly energy prices within a specified date interval, offering insights into cost variations over time. Clients can include query parameters to define the start and end dates, allowing for flexible customization of the data retrieved. Proper authentication, through an authentication token, is required for secure access to this endpoint. A successful request returns an HTTP status code of 200, delivering a dataset that includes timestamped energy prices for each hour within the specified interval. In cases of errors or invalid parameters, the server responds with an appropriate error status code accompanied by a descriptive message to guide users in resolving any issues. This endpoint serves as a valuable tool for monitoring and analyzing historical energy pricing trends within the system.",
            tags = ApiTag.PRICES,
            operationId = "getPriceByRangeOfDates"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Query executed successfully",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(
                                    value = """
                                            [
                                              {
                                                "price": 114.1,
                                                "hour": "2023-10-25T00:00:00+02:00"
                                              },
                                              {
                                                "price": 98.37,
                                                "hour": "2023-10-25T01:00:00+02:00"
                                              },
                                              {
                                                "price": 70.75,
                                                "hour": "2023-10-25T02:00:00+02:00"
                                              },
                                              {
                                                "price": 64.75,
                                                "hour": "2023-10-25T03:00:00+02:00"
                                              },
                                              {
                                                "price": 62.65,
                                                "hour": "2023-10-25T04:00:00+02:00"
                                              },
                                              {
                                                "price": 61.42,
                                                "hour": "2023-10-25T05:00:00+02:00"
                                              },
                                              {
                                                "price": 45.59,
                                                "hour": "2023-10-25T06:00:00+02:00"
                                              },
                                              {
                                                "price": 59.75,
                                                "hour": "2023-10-25T07:00:00+02:00"
                                              },
                                              {
                                                "price": 65.1,
                                                "hour": "2023-10-25T08:00:00+02:00"
                                              },
                                              {
                                                "price": 88.59,
                                                "hour": "2023-10-25T09:00:00+02:00"
                                              },
                                              {
                                                "price": 104.25,
                                                "hour": "2023-10-25T10:00:00+02:00"
                                              },
                                              {
                                                "price": 95,
                                                "hour": "2023-10-25T11:00:00+02:00"
                                              },
                                              {
                                                "price": 70.75,
                                                "hour": "2023-10-25T12:00:00+02:00"
                                              },
                                              {
                                                "price": 62.65,
                                                "hour": "2023-10-25T13:00:00+02:00"
                                              },
                                              {
                                                "price": 45.48,
                                                "hour": "2023-10-25T14:00:00+02:00"
                                              },
                                              {
                                                "price": 35,
                                                "hour": "2023-10-25T15:00:00+02:00"
                                              },
                                              {
                                                "price": 20,
                                                "hour": "2023-10-25T16:00:00+02:00"
                                              },
                                              {
                                                "price": 17.45,
                                                "hour": "2023-10-25T17:00:00+02:00"
                                              },
                                              {
                                                "price": 37.77,
                                                "hour": "2023-10-25T18:00:00+02:00"
                                              },
                                              {
                                                "price": 64.75,
                                                "hour": "2023-10-25T19:00:00+02:00"
                                              },
                                              {
                                                "price": 85,
                                                "hour": "2023-10-25T20:00:00+02:00"
                                              },
                                              {
                                                "price": 97.56,
                                                "hour": "2023-10-25T21:00:00+02:00"
                                              },
                                              {
                                                "price": 109.68,
                                                "hour": "2023-10-25T22:00:00+02:00"
                                              },
                                              {
                                                "price": 105.97,
                                                "hour": "2023-10-25T23:00:00+02:00"
                                              }
                                            ]
                                            """
                            )
                    )
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    public List<PriceByHour> getPriceByRangeOfDates(
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate) {

        return getPriceService.getPricesByRangeOfDates(startDate, endDate);
    }
}
