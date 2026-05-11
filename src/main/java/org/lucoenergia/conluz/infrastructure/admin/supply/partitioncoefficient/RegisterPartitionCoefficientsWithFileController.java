package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.RegisterPartitionCoefficientInBulkService;
import org.lucoenergia.conluz.infrastructure.shared.error.ErrorBuilder;
import org.lucoenergia.conluz.infrastructure.shared.io.CsvFileParser;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.lucoenergia.conluz.infrastructure.shared.web.io.CsvParseExceptionHandler;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping(
        value = "/api/v1/supplies/partition-coefficients/import",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
@Validated
public class RegisterPartitionCoefficientsWithFileController {

    private final PartitionCoefficientFileRequestValidator fileRequestValidator;
    private final CsvFileParser csvFileParser;
    private final MessageSource messageSource;
    private final CsvParseExceptionHandler csvParseExceptionHandler;
    private final ErrorBuilder errorBuilder;
    private final RegisterPartitionCoefficientInBulkService registerPartitionCoefficientInBulkService;

    public RegisterPartitionCoefficientsWithFileController(
            PartitionCoefficientFileRequestValidator fileRequestValidator,
            CsvFileParser csvFileParser,
            MessageSource messageSource,
            CsvParseExceptionHandler csvParseExceptionHandler,
            ErrorBuilder errorBuilder,
            RegisterPartitionCoefficientInBulkService registerPartitionCoefficientInBulkService) {
        this.fileRequestValidator = fileRequestValidator;
        this.csvFileParser = csvFileParser;
        this.messageSource = messageSource;
        this.csvParseExceptionHandler = csvParseExceptionHandler;
        this.errorBuilder = errorBuilder;
        this.registerPartitionCoefficientInBulkService = registerPartitionCoefficientInBulkService;
    }

    @PostMapping
    @Operation(
            summary = "Registers partition coefficients in bulk from a TXT file.",
            description = """
                    Imports the official partition coefficient file produced by the Spanish distribution company.

                    The file must be a plain-text (.txt) file with one supply per line in the format:
                    `CUPS;coefficient` where the coefficient uses a comma as the decimal separator
                    (e.g. `ES0031300806333001KE0F;0,025000`).

                    The file name must follow the pattern: CAU_YYYY.txt, where CAU is the plant identifier
                    and YYYY is the year.

                    Each row closes the currently active coefficient period for that supply and opens a new one
                    starting at `effectiveAt`. Supplies whose CUPS code is not found in the system are recorded
                    as errors and the remaining rows continue to be processed.

                    The response includes a `communityCoefficientSumWarning` if the total sum of active
                    coefficients across all supplies deviates from 100 by more than 0.0001 at `effectiveAt`.
                    This warning is informational only.

                    **Required Role: ADMIN**
                    """,
            tags = ApiTag.SUPPLIES,
            operationId = "importPartitionCoefficientsWithFile",
            security = @SecurityRequirement(name = "bearerToken", scopes = {"ADMIN"})
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "File processed successfully",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = RegisterPartitionCoefficientsWithFileResponse.class))}
            )
    })
    @BadRequestErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity importPartitionCoefficientsWithFile(
            @Parameter(description = "TXT file from the distribution company. Format: CUPS;coefficient (comma decimal separator). File name must follow the pattern: CAU_YYYY.txt.", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "ISO-8601 instant from which the new coefficients are effective.", required = true)
            @RequestParam("effectiveAt") @NotNull Instant effectiveAt
    ) {
        Optional<ResponseEntity<RestError>> validationErrors = fileRequestValidator.validate(file);
        if (validationErrors.isPresent()) {
            return validationErrors.get();
        }

        List<RegisterPartitionCoefficientFileRow> rows;
        try {
            rows = csvFileParser.parse(file.getInputStream(), RegisterPartitionCoefficientFileRow.class, ';');
        } catch (NumberFormatException ex) {
            String message = messageSource.getMessage("error.supply.unable.to.parse.file",
                    Collections.emptyList().toArray(),
                    LocaleContextHolder.getLocale());
            return errorBuilder.build(message, HttpStatus.BAD_REQUEST);
        } catch (Exception ex) {
            return csvParseExceptionHandler.handleCsvParsingError(ex);
        }

        RegisterPartitionCoefficientsWithFileResponse response =
                registerPartitionCoefficientInBulkService.registerInBulk(rows, effectiveAt);

        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
