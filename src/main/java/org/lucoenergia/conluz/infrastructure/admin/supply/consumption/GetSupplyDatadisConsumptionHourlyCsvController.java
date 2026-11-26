package org.lucoenergia.conluz.infrastructure.admin.supply.consumption;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletResponse;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetSupplyDatadisConsumptionCsvService;
import org.lucoenergia.conluz.domain.consumption.datadis.get.SupplyDatadisConsumptionCsv;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.datadis.csv.DatadisHourlyConsumptionCsv;
import org.lucoenergia.conluz.infrastructure.shared.web.FileDownload;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/supplies/{supplyId}/consumption/datadis/hourly/csv")
public class GetSupplyDatadisConsumptionHourlyCsvController {

    private final GetSupplyDatadisConsumptionCsvService service;

    public GetSupplyDatadisConsumptionHourlyCsvController(GetSupplyDatadisConsumptionCsvService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Downloads one month of a supply's hourly Datadis records as a CSV file.",
            description = """
                    Writes the supply's hourly records as Datadis delivered them and Conluz stored them, for one
                    calendar month, to analyse outside Conluz. Nothing is computed from them: unlike
                    `getSupplyHourlyConsumption`, an hour is written as soon as it is stored, whether or not its
                    self-consumption has been published.

                    **Format:** a CSV (`text/csv`, UTF-8 with a BOM, `;` separated, CRLF line ends) named
                    `{CUPS}-{YYYY-MM}.csv`. The header is
                    `cups;date;time;consumptionKWh;obtainMethod;surplusEnergyKWh;generationEnergyKWh;selfConsumptionEnergyKWh`,
                    then one line per stored record. A field holding `;`, `"` or a line end is quoted, with its
                    quotes doubled.

                    - `date` and `time`: the local calendar date (`yyyy/MM/dd`) and the local time (`HH:mm`), in
                      the time zone the application is configured with, at which the record's hour **starts**,
                      `00:00` to `23:00`. They are the `date` and `time` `getSupplyHourlyConsumption` returns
                      for the same hour. Datadis's own labels, which name the hour's **end** (`01:00` for
                      00:00-01:00, up to `24:00`), are not stored.
                    - Every other column is Datadis's value as stored: `cups` and `obtainMethod` unchanged, and
                      every energy figure in kWh, unrounded, in plain notation with a decimal comma
                      (`0,0005`, `1`, `12,345`). A value Datadis did not deliver is an empty field.
                      `generationEnergyKWh` is the distributor's own figure, not the supply's assigned
                      production.

                    **Order:** lines follow the stored instants, oldest first. On the day the clocks go back,
                    the repeated local hour is two consecutive lines with the same `date` and `time`, the
                    earlier instant first; on the day they go forward, the skipped local hour has no line.

                    **Absent hours:** an hour without a stored record has no line. A month without any record
                    is answered with the header line only.

                    **Month:** `month` is required, as `YYYY-MM`, and covers the hours that start from the
                    first day of the month at 00:00 up to, not including, the first day of the next month at
                    00:00, in the time zone the application is configured with. The current month may be
                    requested and holds the records stored so far; a later month is a bad request with code
                    `CONSUMPTION_CSV_MONTH_IN_FUTURE`. There is no earliest month. A disabled supply still has
                    every stored record of the month written.

                    **Required: the caller can read the supply** (the supply's `canRead` capability), exactly
                    as for `getSupplyHourlyConsumption`: its owner, or an active community admin of its
                    community. Every other caller, a platform admin who is not a member included, is answered
                    404, and a request without a token 401.
                    """,
            tags = ApiTag.SUPPLIES,
            operationId = "getSupplyDatadisConsumptionHourlyCsv",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "The month's hourly Datadis records as a CSV file",
                    content = @Content(mediaType = "text/csv",
                            schema = @Schema(type = "string", format = "binary"))
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canReadSupply(#supplyId)")
    public ResponseEntity<byte[]> getSupplyDatadisConsumptionHourlyCsv(
            @PathVariable("supplyId") UUID supplyId,
            @Parameter(description = "The calendar month to export, as YYYY-MM.", required = true,
                    schema = @Schema(type = "string", pattern = "^\\d{4}-(0[1-9]|1[0-2])$", example = "2026-09"))
            @RequestParam(value = "month")
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            HttpServletResponse response) {

        SupplyDatadisConsumptionCsv file = service.getHourlyCsv(SupplyId.of(supplyId), month);

        return FileDownload.attachment(response, file.filename(),
                MediaType.parseMediaType(DatadisHourlyConsumptionCsv.CONTENT_TYPE), file.content());
    }
}
