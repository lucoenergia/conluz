package org.lucoenergia.conluz.infrastructure.consumption.datadis.report;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.access.CommunityAccessGuard;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionReportService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/communities/{communityId}/consumption/datadis/report/hourly")
public class GetDatadisConsumptionCsvReportController {

    private final GetDatadisConsumptionReportService getDatadisConsumptionReportService;
    private final CommunityAccessGuard communityAccessGuard;

    public GetDatadisConsumptionCsvReportController(GetDatadisConsumptionReportService getDatadisConsumptionReportService,
                                                    CommunityAccessGuard communityAccessGuard) {
        this.getDatadisConsumptionReportService = getDatadisConsumptionReportService;
        this.communityAccessGuard = communityAccessGuard;
    }

    @GetMapping(value = "/csv")
    @Operation(
            summary = "Exports hourly consumption data of a community's supplies as a CSV file",
            description = """
                    Retrieves hourly consumption data for the supplies of the community identified by the path
                    `communityId` within the specified date range and returns the result as a downloadable CSV file.

                    **Authorization Rules:**
                    - Only a Community Admin of the community can access this endpoint.
                    - Returns 404 if the community does not exist or the caller cannot manage it.

                    The CSV includes:
                    - cups: Supply point identifier
                    - date: Date of the consumption record
                    - time: Time of the consumption record
                    - consumptionKWh: Total consumption in kWh
                    - obtainMethod: Real or Estimated
                    - surplusEnergyKWh: Energy sent to the grid in kWh
                    - generationEnergyKWh: Generated energy in kWh
                    - selfConsumptionEnergyKWh: Self-consumed energy in kWh
                    """,
            tags = ApiTag.CONSUMPTION,
            operationId = "getDatadisConsumptionHourlyCsvReport",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "CSV file with hourly consumption data for the community's supplies"
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> getDatadisConsumptionHourlyCsvReport(
            @PathVariable UUID communityId,
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate) {

        if (!communityAccessGuard.canManageCommunity(communityId)) {
            throw new CommunityNotFoundException(communityId);
        }

        ByteArrayOutputStream out =
                getDatadisConsumptionReportService.getHourlyConsumptionReportAsCsv(startDate, endDate, communityId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "consumptions.csv");

        return ResponseEntity.ok()
                .headers(headers)
                .body(out.toByteArray());
    }
}
