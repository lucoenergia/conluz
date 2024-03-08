package org.lucoenergia.conluz.infrastructure.admin.user.create;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserService;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collections;
import java.util.List;
import java.util.Optional;


/**
 * Controller class for importing users in bulk from a CSV file.
 */
@RestController
@RequestMapping(
        value = "/api/v1/users/import",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
@Validated
public class CreateUsersWithFileController {

    private final CsvFileRequestValidator csvFileRequestValidator;
    private final MessageSource messageSource;
    private final CreateUserService createUserService;

    public CreateUsersWithFileController(CsvFileRequestValidator csvFileRequestValidator, MessageSource messageSource,
                                         CreateUserService createUserService) {
        this.csvFileRequestValidator = csvFileRequestValidator;
        this.messageSource = messageSource;
        this.createUserService = createUserService;
    }

    @PostMapping
    @Operation(
            summary = "Creates users in bulk importing a CSV file.",
            description = """
                    This endpoint facilitates the creation of a set of users within the system by importing a CSV file.
                                    
                    This endpoint requires clients to send a request containing a file with essential details for each user, including username, password, and any additional relevant information.
                                    
                    Authentication is mandated, utilizing an authentication token, to ensure secure access.
                                    
                    Upon successful file processing, the server responds with an HTTP status code of 200, along with comprehensive details about the result of the bulk operation, including what users have been created or any potential error.
                                    
                    In cases where the creation process encounters errors, the server responds with an appropriate error status code, accompanied by a descriptive error message to guide clients in addressing and resolving the issue.
                    """,
            tags = ApiTag.USERS,
            operationId = "createUsersWithFile"
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
    public ResponseEntity createUsersWithFile(@RequestParam("file") MultipartFile file) {

        Optional<ResponseEntity<RestError>> optionalResponseEntity = csvFileRequestValidator.validate(file);
        if (optionalResponseEntity.isPresent()) {
            return optionalResponseEntity.get();
        }
        CreateUsersInBulkResponse response = new CreateUsersInBulkResponse();

        try (Reader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {

            // create csv bean reader
            CsvToBean<CreateUserBody> csvToBean = new CsvToBeanBuilder<CreateUserBody>(reader)
                    .withType(CreateUserBody.class)
                    .withIgnoreLeadingWhiteSpace(true)
                    .build();

            // convert `CsvToBean` object to list of users
            List<CreateUserBody> users = csvToBean.parse();

            // save users in DB
            users.forEach(user -> {
                try {
                    User newUser = createUserService.create(user.mapToUser());
                    response.addCreated(UserPersonalId.of(newUser.getPersonalId()));
                } catch (UserAlreadyExistsException e) {
                    response.addError(UserPersonalId.of(user.getPersonalId()),
                            messageSource.getMessage("error.user.already.exists",
                                    Collections.singletonList(user.getPersonalId()).toArray(),
                                    LocaleContextHolder.getLocale()));
                } catch (Exception e) {
                    response.addError(UserPersonalId.of(user.getPersonalId()),
                            messageSource.getMessage("error.user.unable.to.create", new List[]{},
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

    private ResponseEntity<RestError> buildUnsupportedMediaTypeErrorResponse(String contentType) {
        String message = messageSource.getMessage(
                "error.http.media.type.not.supported",
                Collections.singletonList(contentType).toArray(),
                LocaleContextHolder.getLocale()
        );
        return new ResponseEntity<>(new RestError(HttpStatus.BAD_REQUEST.value(), message), HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<RestError> buildUnsupportedExtensionErrorResponse() {
        String message = messageSource.getMessage(
                "error.http.extension.not.supported",
                new List[]{},
                LocaleContextHolder.getLocale()
        );
        return new ResponseEntity<>(new RestError(HttpStatus.BAD_REQUEST.value(), message), HttpStatus.BAD_REQUEST);
    }
}
