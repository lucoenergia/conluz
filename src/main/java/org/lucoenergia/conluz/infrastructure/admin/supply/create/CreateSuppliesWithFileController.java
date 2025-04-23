package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyService;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.shared.io.CsvFileParser;
import org.lucoenergia.conluz.infrastructure.shared.io.CsvFileRequestValidator;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.lucoenergia.conluz.infrastructure.shared.web.io.CsvParseExceptionHandler;
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


/**
 * Controller class for importing supplies in bulk from a CSV file.
 */
@RestController
@RequestMapping(
        value = "/api/v1/supplies/import",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
@Validated
public class CreateSuppliesWithFileController {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateSuppliesWithFileController.class);

    private final CsvFileRequestValidator csvFileRequestValidator;
    private final CsvFileParser csvFileParser;
    private final MessageSource messageSource;
    private final CreateSupplyService createSupplyService;
    private final CsvParseExceptionHandler csvParseExceptionHandler;

    public CreateSuppliesWithFileController(CsvFileRequestValidator csvFileRequestValidator,
                                            CsvFileParser csvFileParser, MessageSource messageSource,
                                            CreateSupplyService createSupplyService, CsvParseExceptionHandler csvParseExceptionHandler) {
        this.csvFileRequestValidator = csvFileRequestValidator;
        this.csvFileParser = csvFileParser;
        this.messageSource = messageSource;
        this.createSupplyService = createSupplyService;
        this.csvParseExceptionHandler = csvParseExceptionHandler;
    }

    @PostMapping
    @Operation(
            summary = "Creates supplies in bulk importing a CSV file.",
            description = """
                    This endpoint facilitates the creation of a set of supplies within the system by importing a CSV file.
                    
                    This endpoint requires clients to send a request containing a file with essential details for each supply, including code, address, users and any additional relevant information.
                    
                    Authentication is mandated, utilizing an authentication token, to ensure secure access.
                    
                    Upon successful file processing, the server responds with an HTTP status code of 200, along with comprehensive details about the result of the bulk operation, including what users have been created or any potential error.
                    
                    In cases where the creation process encounters errors, the server responds with an appropriate error status code, accompanied by a descriptive error message to guide clients in addressing and resolving the issue.
                    """,
            tags = ApiTag.SUPPLIES,
            operationId = "createSuppliesWithFile"
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
    public ResponseEntity createSuppliesWithFile(
            @Parameter(description = "CSV file format: code(String), address(String), partitionCoefficient(Float), address(String), personalId(String).")
            @RequestParam("file") MultipartFile file) {

        Optional<ResponseEntity<RestError>> optionalResponseEntity = csvFileRequestValidator.validate(file);
        if (optionalResponseEntity.isPresent()) {
            return optionalResponseEntity.get();
        }
        CreationInBulkResponse<String, String> response = new CreationInBulkResponse<>();

        List<CreateSupplyBody> supplies;
        try {
            supplies = csvFileParser.parse(file.getInputStream(), CreateSupplyBody.class);
        } catch (Exception ex) {
            return csvParseExceptionHandler.handleCsvParsingError(ex);
        }

        // save supplies in DB
        supplies.forEach(supply -> {
            try {
                Supply newSupply = createSupplyService.create(supply.mapToSupply(), UserPersonalId.of(supply.getPersonalId()));
                response.addCreated(newSupply.getCode());
            } catch (SupplyAlreadyExistsException e) {
                LOGGER.error("Supply already exists", e);
                response.addError(supply.getCode(),
                        messageSource.getMessage("error.supply.already.exists",
                                Collections.singletonList(supply.getCode()).toArray(),
                                LocaleContextHolder.getLocale()));
            } catch (UserNotFoundException e) {
                LOGGER.error("User not found", e);
                response.addError(supply.getCode(),
                        messageSource.getMessage("error.user.not.found",
                                Collections.singletonList(supply.getPersonalId()).toArray(),
                                LocaleContextHolder.getLocale()));
            } catch (Exception e) {
                LOGGER.error("Unable to create supply", e);
                response.addError(supply.getCode(),
                        messageSource.getMessage("error.supply.unable.to.create", new List[]{},
                                LocaleContextHolder.getLocale()));
            }
        });

        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
