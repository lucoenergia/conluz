package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.lucoenergia.conluz.domain.admin.supply.CreateSupplyService;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.shared.io.CsvFileRequestValidator;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
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

    private final CsvFileRequestValidator csvFileRequestValidator;
    private final MessageSource messageSource;
    private final CreateSupplyService createSupplyService;

    public CreateSuppliesWithFileController(CsvFileRequestValidator csvFileRequestValidator, MessageSource messageSource,
                                            CreateSupplyService createSupplyService) {
        this.csvFileRequestValidator = csvFileRequestValidator;
        this.messageSource = messageSource;
        this.createSupplyService = createSupplyService;
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
                    useReturnTypeSchema = true
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    public ResponseEntity createSuppliesWithFile(@RequestParam("file") MultipartFile file) {

        Optional<ResponseEntity<RestError>> optionalResponseEntity = csvFileRequestValidator.validate(file);
        if (optionalResponseEntity.isPresent()) {
            return optionalResponseEntity.get();
        }
        CreateSuppliesInBulkResponse response = new CreateSuppliesInBulkResponse();

        try (Reader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {

            // create csv bean reader
            CsvToBean<CreateSupplyBody> csvToBean = new CsvToBeanBuilder<CreateSupplyBody>(reader)
                    .withType(CreateSupplyBody.class)
                    .withIgnoreLeadingWhiteSpace(true)
                    .build();

            // convert `CsvToBean` object to list of supplies
            List<CreateSupplyBody> supplies = csvToBean.parse();

            // save supplies in DB
            supplies.forEach(supply -> {
                try {
                    Supply newSupply = createSupplyService.create(supply.mapToSupply(), UserPersonalId.of(supply.getPersonalId()));
                    response.addCreated(SupplyCode.of(newSupply.getCode()));
                } catch (SupplyAlreadyExistsException e) {
                    response.addError(SupplyCode.of(supply.getCode()),
                            messageSource.getMessage("error.supply.already.exists",
                                    Collections.singletonList(supply.getCode()).toArray(),
                                    LocaleContextHolder.getLocale()));
                } catch (UserNotFoundException e) {
                    response.addError(SupplyCode.of(supply.getCode()),
                            messageSource.getMessage("error.user.not.found",
                                    Collections.singletonList(supply.getPersonalId()).toArray(),
                                    LocaleContextHolder.getLocale()));
                } catch (Exception e) {
                    response.addError(SupplyCode.of(supply.getCode()),
                            messageSource.getMessage("error.supply.unable.to.create", new List[]{},
                            LocaleContextHolder.getLocale()));
                }
            });
        } catch (Exception ex) {
            if (ex.getCause() instanceof CsvRequiredFieldEmptyException) {
                return new ResponseEntity<>(new RestError(HttpStatus.BAD_REQUEST.value(),
                        messageSource.getMessage("error.fields.number.does.not.match", new List[]{},
                                LocaleContextHolder.getLocale())),
                        HttpStatus.BAD_REQUEST);
            }
            return new ResponseEntity<>(new RestError(HttpStatus.BAD_REQUEST.value(),
                    messageSource.getMessage("error.bad.request", new List[]{},
                    LocaleContextHolder.getLocale())),
                    HttpStatus.BAD_REQUEST);
        }

        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
