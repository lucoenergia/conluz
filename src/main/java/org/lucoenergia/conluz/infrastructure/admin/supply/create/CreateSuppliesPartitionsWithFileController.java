package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementId;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.SupplyAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.supply.SupplyPartition;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyPartitionService;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.infrastructure.shared.io.CsvFileParser;
import org.lucoenergia.conluz.infrastructure.shared.io.CsvFileRequestValidator;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.lucoenergia.conluz.infrastructure.shared.web.response.CreationInBulkResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    public CreateSuppliesPartitionsWithFileController(CsvFileRequestValidator csvFileRequestValidator,
                                                      CsvFileParser csvFileParser, MessageSource messageSource,
                                                      CreateSupplyPartitionService createSupplyPartitionService) {
        this.csvFileRequestValidator = csvFileRequestValidator;
        this.csvFileParser = csvFileParser;
        this.messageSource = messageSource;
        this.createSupplyPartitionService = createSupplyPartitionService;
    }

    @PostMapping
    @Operation(
            summary = "Creates supplies partitions in bulk importing a CSV file.",
            description = """
                    This endpoint facilitates the creation of a set of supplies partitions within the system by importing a CSV file.
                    
                    This endpoint requires clients to send a request containing a file with an identifier and the coefficient for each supply.
                    
                    Authentication is mandated, utilizing an authentication token, to ensure secure access.
                    
                    Upon successful file processing, the server responds with an HTTP status code of 200, along with comprehensive details about the result of the bulk operation, including what supplies partitions have been created or any potential error.
                    
                    In cases where the creation process encounters errors, the server responds with an appropriate error status code, accompanied by a descriptive error message to guide clients in addressing and resolving the issue.
                    """,
            tags = ApiTag.SUPPLIES,
            operationId = "importSuppliesPartitionsWithFile"
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
        CreationInBulkResponse<CreateSupplyPartitionDto, SupplyPartition> response = new CreationInBulkResponse<>();

        List<CreateSupplyPartitionDto> suppliesPartitions;
        try {
            suppliesPartitions = csvFileParser.parse(file.getInputStream(),
                    CreateSupplyPartitionDto.class);
        } catch (Exception ex) {
            if (ex.getCause() instanceof CsvRequiredFieldEmptyException) {
                LOGGER.error("Error parsing file", ex.getCause());
                return new ResponseEntity<>(new RestError(HttpStatus.BAD_REQUEST.value(),
                        messageSource.getMessage("error.fields.number.does.not.match", new List[]{},
                                LocaleContextHolder.getLocale())),
                        HttpStatus.BAD_REQUEST);
            }
            if (ex.getCause() instanceof CsvDataTypeMismatchException) {
                LOGGER.error("Error parsing line", ex.getCause());
                return new ResponseEntity<>(new RestError(HttpStatus.BAD_REQUEST.value(),
                        messageSource.getMessage("error.supply.unable.to.parse.file", new List[]{},
                                LocaleContextHolder.getLocale())),
                        HttpStatus.BAD_REQUEST);
            }
            LOGGER.error("Error processing file", ex);
            return new ResponseEntity<>(new RestError(HttpStatus.BAD_REQUEST.value(),
                    messageSource.getMessage("error.bad.request", new List[]{},
                            LocaleContextHolder.getLocale())),
                    HttpStatus.BAD_REQUEST);
        }

        try {
            createSupplyPartitionService.validateTotalCoefficient(suppliesPartitions);
        } catch (IllegalArgumentException e) {
            LOGGER.error("Invalid total coefficient.", e);
            response.addError(null,
                    messageSource.getMessage("error.supply.already.exists",
                            Collections.emptyList().toArray(),
                            LocaleContextHolder.getLocale()));
        }

        suppliesPartitions.forEach(supplyPartition -> {
            try {
                SupplyPartition newSupplyPartition = createSupplyPartitionService.create(SupplyCode.of(supplyPartition.getCode()),
                        supplyPartition.getCoefficient(),
                        SharingAgreementId.of(sharingAgreementId)
                );
                response.addCreated(newSupplyPartition);
            } catch (SupplyAlreadyExistsException e) {
                LOGGER.error("Supply already exists", e);
                response.addError(supplyPartition,
                        messageSource.getMessage("error.supply.already.exists",
                                Collections.singletonList(supplyPartition.getCode()).toArray(),
                                LocaleContextHolder.getLocale()));
            } catch (SharingAgreementNotFoundException e) {
                LOGGER.error("Sharing agreement not found", e);
                response.addError(supplyPartition,
                        messageSource.getMessage("error.sharing.agreement.not.found",
                                Collections.singletonList(sharingAgreementId).toArray(),
                                LocaleContextHolder.getLocale()));
            } catch (Exception e) {
                LOGGER.error("Unable to create supply partition", e);
                response.addError(supplyPartition,
                        messageSource.getMessage("error.supply.unable.to.create", new List[]{},
                                LocaleContextHolder.getLocale()));
            }
        });

        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
