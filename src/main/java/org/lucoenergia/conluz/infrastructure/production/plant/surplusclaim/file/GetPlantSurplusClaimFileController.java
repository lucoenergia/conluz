package org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim.file;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletResponse;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.file.GetPlantSurplusClaimFileService;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.file.SurplusClaimFile;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.file.SurplusClaimFileType;
import org.lucoenergia.conluz.infrastructure.shared.web.FileDownload;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
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
@RequestMapping("/api/v1/plants/{plantId}/surplus-claim/file")
public class GetPlantSurplusClaimFileController {

    private final GetPlantSurplusClaimFileService service;

    public GetPlantSurplusClaimFileController(GetPlantSurplusClaimFileService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Downloads the file a plant's surplus is claimed with from its retailer for one closed month.",
            description = """
                    Writes, for one closed month, the plant's surplus claim as a file to attach to the claim sent
                    to the retailer. It carries exactly the days, the day states and the hours that
                    `getPlantSurplusClaim` previews for that month, from the unrounded values. Nothing is stored:
                    it is written on every request. Its warnings never prevent the file, and none of them, nor the
                    summary or the income, is written into it.

                    **Formats:**

                    - `ENDESA`: Endesa's own template, `reclamacionMedidas_{CIL}_{YYYY-MM}.xlsx`, delivered as
                      `reclamacionMedidas_{CIL}_{YYYY-MM}.zip` (`application/zip`) holding only the workbook. Its
                      `Carga` sheet gets one row per local day from row 2: the CIL, `AS`, the day's state (`R`
                      real, `E` estimated), the date shown as `dd/mm/yyyy`, then the hours `HOR1`... as numbers
                      rounded half-up to 2 decimals. An hour without any record is written as `0`, and an hour the
                      day does not have (`HOR24` and `HOR25` on the 23-hour day, `HOR25` on a 24-hour day) is left
                      empty. The other sheets are the template's. A plant without a CIL is a conflict with code
                      `SURPLUS_CLAIM_CIL_REQUIRED`.
                    - `GENERIC`: a CSV (`text/csv`, UTF-8 with a BOM, `;` separated, CRLF line ends) named
                      `excedentes_{regulatoryCode}_{YYYY-MM}.csv`, or after the plant's id when it has no
                      regulatory code. The header is `fecha;estado;HOR1;...;HOR25`, then one row of 27 fields per
                      local day: the date as `dd/mm/yyyy`, `R` or `E`, and the 25 hours with 3 decimals and a
                      decimal comma. An hour without any record and an hour the day does not have are both empty;
                      the state tells them apart. It never needs a CIL.

                    **Month:** `month` is required and names a closed calendar month, the one the admin reviewed.
                    The current month and later months are a bad request with code
                    `SURPLUS_CLAIM_MONTH_NOT_CLOSED`.

                    **Required: the caller can manage the plant** (the plant's `canManage` capability): an
                    active community admin of the plant's community. A member of that community who is not an
                    admin is answered 403, and every other caller, a platform admin who is not a member
                    included, 404.
                    """,
            tags = ApiTag.PLANTS,
            operationId = "getPlantSurplusClaimFile",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "The claim file: a ZIP holding Endesa's workbook, or the generic CSV",
                    content = {
                            @Content(mediaType = "application/zip",
                                    schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = "text/csv",
                                    schema = @Schema(type = "string", format = "binary"))
                    }
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The Endesa format was requested for a plant without a CIL "
                            + "(`SURPLUS_CLAIM_CIL_REQUIRED`).",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class))
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canManagePlant(#plantId)")
    public ResponseEntity<byte[]> getPlantSurplusClaimFile(
            @PathVariable("plantId") UUID plantId,
            @Parameter(description = "The closed calendar month to claim, as YYYY-MM.", required = true,
                    schema = @Schema(type = "string", pattern = "^\\d{4}-(0[1-9]|1[0-2])$", example = "2026-09"))
            @RequestParam(value = "month")
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @Parameter(description = "The format of the file.", required = true)
            @RequestParam(value = "format") SurplusClaimFileType format,
            HttpServletResponse response) {

        SurplusClaimFile file = service.getSurplusClaimFile(plantId, month, format);

        return FileDownload.attachment(response, file.filename(), MediaType.parseMediaType(file.contentType()),
                file.content());
    }
}
