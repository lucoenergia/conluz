package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.PartitionCoefficientService;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.infrastructure.shared.error.ErrorBuilder;
import org.lucoenergia.conluz.infrastructure.shared.io.CsvFileParser;
import org.lucoenergia.conluz.infrastructure.shared.io.TxtFileRequestValidator;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.lucoenergia.conluz.infrastructure.shared.web.io.CsvParseExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Registers partition coefficients in bulk from a TXT file produced by the Spanish distribution company.
 * File format: one row per supply, semicolon-separated, with comma as decimal separator.
 * Example: {@code ES0031300806333001KE0F;0,025000}
 */
@RestController
@RequestMapping(
        value = "/api/v1/supplies/partition-coefficients/import",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
@Validated
public class RegisterPartitionCoefficientsWithFileController {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegisterPartitionCoefficientsWithFileController.class);

    private final TxtFileRequestValidator txtFileRequestValidator;
    private final CsvFileParser csvFileParser;
    private final GetSupplyRepository getSupplyRepository;
    private final PartitionCoefficientService partitionCoefficientService;
    private final MessageSource messageSource;
    private final CsvParseExceptionHandler csvParseExceptionHandler;
    private final ErrorBuilder errorBuilder;

    public RegisterPartitionCoefficientsWithFileController(
            TxtFileRequestValidator txtFileRequestValidator,
            CsvFileParser csvFileParser,
            GetSupplyRepository getSupplyRepository,
            PartitionCoefficientService partitionCoefficientService,
            MessageSource messageSource,
            CsvParseExceptionHandler csvParseExceptionHandler,
            ErrorBuilder errorBuilder) {
        this.txtFileRequestValidator = txtFileRequestValidator;
        this.csvFileParser = csvFileParser;
        this.getSupplyRepository = getSupplyRepository;
        this.partitionCoefficientService = partitionCoefficientService;
        this.messageSource = messageSource;
        this.csvParseExceptionHandler = csvParseExceptionHandler;
        this.errorBuilder = errorBuilder;
    }

    @PostMapping
    @Operation(
            summary = "Registers partition coefficients in bulk from a TXT file.",
            description = """
                    Imports the official partition coefficient file produced by the Spanish distribution company.

                    The file must be a plain-text (.txt) file with one supply per line in the format:
                    `CUPS;coefficient` where the coefficient uses a comma as the decimal separator
                    (e.g. `ES0031300806333001KE0F;0,025000`).

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
            @Parameter(description = "TXT file from the distribution company. Format: CUPS;coefficient (comma decimal separator).", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "ISO-8601 instant from which the new coefficients are effective.", required = true)
            @RequestParam("effectiveAt") @NotNull Instant effectiveAt
    ) {
        Optional<ResponseEntity<RestError>> validationErrors = txtFileRequestValidator.validate(file);
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

        RegisterPartitionCoefficientsWithFileResponse response = new RegisterPartitionCoefficientsWithFileResponse();

        for (RegisterPartitionCoefficientFileRow row : rows) {
            Optional<Supply> supply = getSupplyRepository.findByCode(SupplyCode.of(row.getCups()));
            if (supply.isEmpty()) {
                String message = messageSource.getMessage(
                        "error.supply.not.found.by.code",
                        new Object[]{row.getCups()},
                        LocaleContextHolder.getLocale());
                response.addError(row.getCups(), message);
                continue;
            }
            try {
                SupplyPartitionCoefficient saved = partitionCoefficientService.registerCoefficientChange(
                        supply.get().getId(), row.getCoefficient(), effectiveAt);
                response.addCreated(new PartitionCoefficientResponse(saved));
            } catch (Exception ex) {
                LOGGER.error("Error registering coefficient for CUPS {}", row.getCups(), ex);
                response.addError(row.getCups(), ex.getMessage());
            }
        }

        BigDecimal communitySum = partitionCoefficientService.computeCommunitySum(effectiveAt);
        response.applyCommunitySum(communitySum);

        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
