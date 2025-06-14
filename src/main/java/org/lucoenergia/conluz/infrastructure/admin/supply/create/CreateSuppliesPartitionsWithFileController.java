package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementId;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.SupplyPartition;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyPartitionService;
import org.lucoenergia.conluz.infrastructure.admin.supply.InvalidSupplyPartitionCoefficientException;
import org.lucoenergia.conluz.infrastructure.shared.io.CsvFileParser;
import org.lucoenergia.conluz.infrastructure.shared.io.CsvFileRequestValidator;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.lucoenergia.conluz.infrastructure.shared.web.io.CsvParseExceptionHandler;
import org.lucoenergia.conluz.domain.shared.response.CreationInBulkResponse;
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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


/**
 * Controller class for importing supplies in bulk from a CSV file.
 */
@RestController
@RequestMapping(
        value = "/api/v1/supplies/partitions/import",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
@Validated
public class CreateSuppliesPartitionsWithFileController {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateSuppliesPartitionsWithFileController.class);

    private final CsvFileRequestValidator csvFileRequestValidator;
    private final CsvFileParser csvFileParser;
    private final MessageSource messageSource;
    private final CreateSupplyPartitionService createSupplyPartitionService;
    private final CsvParseExceptionHandler csvParseExceptionHandler;

    public CreateSuppliesPartitionsWithFileController(CsvFileRequestValidator csvFileRequestValidator,
                                                      CsvFileParser csvFileParser, MessageSource messageSource,
                                                      CreateSupplyPartitionService createSupplyPartitionService, CsvParseExceptionHandler csvParseExceptionHandler) {
        this.csvFileRequestValidator = csvFileRequestValidator;
        this.csvFileParser = csvFileParser;
        this.messageSource = messageSource;
        this.createSupplyPartitionService = createSupplyPartitionService;
        this.csvParseExceptionHandler = csvParseExceptionHandler;
    }

    @PostMapping
    @Operation(
            summary = "Creates supplies partitions in bulk importing a CSV file.",
            description = """
                    This endpoint facilitates the creation of a set of supplies partitions within the system by
                    importing a CSV file.
                    
                    This endpoint requires clients to send a request containing a file with an identifier and the
                    coefficient for each supply.
                    
                    Authentication is mandated, utilizing an authentication token, to ensure secure access.
                    **Required Role: ADMIN**
                    
                    Upon successful file processing, the server responds with an HTTP status code of 200, along with
                    comprehensive details about the result of the bulk operation, including what supplies partitions
                    have been created or any potential error.
                    
                    In cases where the creation process encounters errors, the server responds with an appropriate error
                    status code, accompanied by a descriptive error message to guide clients in addressing and resolving
                    the issue.
                    """,
            tags = ApiTag.SUPPLIES,
            operationId = "importSuppliesPartitionsWithFile",
            security = @SecurityRequirement(name = "bearerToken", scopes = {"ADMIN"})
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "File processed successfully",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = CreationInBulkResponse.class))}
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity importSuppliesWithFile(
            @Parameter(description = "CSV file format: code(String), coefficient(Float).", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Identifier of the sharing agreement", required = true)
            @RequestParam("sharingAgreementId") UUID sharingAgreementId
    ) {

        Optional<ResponseEntity<RestError>> validationErrors = csvFileRequestValidator.validate(file);
        if (validationErrors.isPresent()) {
            return validationErrors.get();
        }

        List<CreateSupplyPartitionDto> suppliesPartitions;
        try {
            suppliesPartitions = csvFileParser.parse(file.getInputStream(), CreateSupplyPartitionDto.class, ';');
        } catch (NumberFormatException ex) {
            LOGGER.error("Invalid number format.", ex);
            String message = messageSource.getMessage("error.supply.partitions.invalid.number.format",
                    Collections.emptyList().toArray(),
                    LocaleContextHolder.getLocale());
            return new ResponseEntity<>(
                    new RestError(HttpStatus.BAD_REQUEST.value(), message),
                    HttpStatus.BAD_REQUEST
            );
        } catch (Exception ex) {
            return csvParseExceptionHandler.handleCsvParsingError(ex);
        }

        CreationInBulkResponse<CreateSupplyPartitionDto, SupplyPartition> response;
        try {
            response = createSupplyPartitionService.createInBulk(suppliesPartitions, SharingAgreementId.of(sharingAgreementId));
        } catch (InvalidSupplyPartitionCoefficientException e) {
            return new ResponseEntity<>(
                    new RestError(HttpStatus.BAD_REQUEST.value(), e.getMessage()),
                    HttpStatus.BAD_REQUEST
            );
        } catch (SharingAgreementNotFoundException e) {
            LOGGER.error("Sharing agreement with id {} not found", sharingAgreementId, e);
            String message = messageSource.getMessage("error.sharing.agreement.not.found",
                            Collections.singletonList(sharingAgreementId).toArray(),
                            LocaleContextHolder.getLocale());
            return new ResponseEntity<>(
                    new RestError(HttpStatus.BAD_REQUEST.value(), message),
                    HttpStatus.BAD_REQUEST
            );
        }

        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
