package org.lucoenergia.conluz.infrastructure.admin.user.create;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserService;
import org.lucoenergia.conluz.domain.admin.user.create.ImportRowCommunityMismatchException;
import org.lucoenergia.conluz.domain.admin.user.password.PasswordPolicyViolationException;
import org.lucoenergia.conluz.infrastructure.admin.user.password.PasswordPolicyMessages;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.shared.error.ErrorBuilder;
import org.lucoenergia.conluz.infrastructure.shared.io.CsvFileRequestValidator;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


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
    private final ErrorBuilder errorBuilder;
    private final PasswordPolicyMessages passwordPolicyMessages;

    public CreateUsersWithFileController(CsvFileRequestValidator csvFileRequestValidator, MessageSource messageSource,
                                         CreateUserService createUserService, ErrorBuilder errorBuilder,
                                         PasswordPolicyMessages passwordPolicyMessages) {
        this.csvFileRequestValidator = csvFileRequestValidator;
        this.messageSource = messageSource;
        this.createUserService = createUserService;
        this.errorBuilder = errorBuilder;
        this.passwordPolicyMessages = passwordPolicyMessages;
    }

    @PostMapping
    @Operation(
            summary = "Creates users in bulk importing a CSV file.",
            description = """
                    This endpoint facilitates the creation of a set of users within the system by importing a CSV file.
                                    
                    This endpoint requires clients to send a request containing a file with essential details for each user, including username, password, and any additional relevant information.
                                    
                    The `personalId` of every row is normalised before it is stored or compared: surrounding and inner whitespace (including the no-break space), dots and hyphens are removed and letters are upper-cased, so `12.345.678-a` is stored as `12345678A`. A row whose normalised `personalId` already belongs to a user is not created and is reported in `errors`; the error message does not repeat the value.
                                    
                    Every row is applied only to the community given by the `communityId` query parameter. A row whose `communityId` column is present and differs from the query parameter, or is not a valid UUID, is rejected and reported in `errors`; no user and no membership are created for it.
                                    
                    The `password` of every row must satisfy the password policy: between 15 and 64 characters, counting each Unicode code point as one, and no more than 72 bytes once UTF-8 encoded. Any character is accepted, including spaces and non-ASCII letters, and there are no composition rules. A row whose password breaks the policy is not created and is reported in `errors` with a message naming the rule that failed; the other rows are still processed. Every user created by an import is flagged as having to change their password.
                                    
                    Authentication is mandated, utilizing an authentication token, to ensure secure access.
                    **Required: Platform Admin or Community Admin**
                                    
                    Upon successful file processing, the server responds with an HTTP status code of 200, along with comprehensive details about the result of the bulk operation, including what users have been created or any potential error.
                                    
                    In cases where the creation process encounters errors, the server responds with an appropriate error status code, accompanied by a descriptive error message to guide clients in addressing and resolving the issue.
                    """,
            tags = ApiTag.USERS,
            operationId = "createUsersWithFile",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "File processed successfully",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = CreateUsersInBulkResponse.class))}
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canCreateUserIn(#communityId)")
    public ResponseEntity createUsersWithFile(
            @Parameter(description="CSV file format: number(Integer), fullName(String), personalId(String; normalised: whitespace, dots and hyphens removed, letters upper-cased), address(String), email(String), phoneNumber(String), role(String), password(String; 15 to 64 characters counted as Unicode code points and at most 72 bytes in UTF-8, any character accepted, no composition rules; a row that breaks this is rejected and reported in errors), communityId(UUID, optional; if present it must equal the communityId query parameter, otherwise the row is rejected), communityRole(COMMUNITY_MEMBER|COMMUNITY_ADMIN, optional).")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Target community UUID. Required for community admins; optional for platform admins.")
            @RequestParam(value = "communityId", required = false) UUID communityId) {

        Optional<ResponseEntity<RestError>> optionalResponseEntity = csvFileRequestValidator.validate(file);
        if (optionalResponseEntity.isPresent()) {
            return optionalResponseEntity.get();
        }
        CreateUsersInBulkResponse response = new CreateUsersInBulkResponse();

        try (Reader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {

            CsvToBean<CreateUserCsvBody> csvToBean = new CsvToBeanBuilder<CreateUserCsvBody>(reader)
                    .withType(CreateUserCsvBody.class)
                    .withIgnoreLeadingWhiteSpace(true)
                    .build();

            List<CreateUserCsvBody> users = csvToBean.parse();

            users.forEach(user -> {
                try {
                    User newUser = createUserService.createFromImport(user.mapToUser(), user.getCommunityId(),
                            communityId, user.getCommunityRole());
                    response.addCreated(UserPersonalId.of(newUser.getPersonalId()));
                } catch (ImportRowCommunityMismatchException e) {
                    response.addError(UserPersonalId.of(user.getPersonalId()),
                            messageSource.getMessage("error.user.import.community.mismatch", new List[]{},
                                    LocaleContextHolder.getLocale()));
                } catch (PasswordPolicyViolationException e) {
                    response.addError(UserPersonalId.of(user.getPersonalId()),
                            passwordPolicyMessages.messageFor(e.getRule()));
                } catch (UserAlreadyExistsException e) {
                    response.addError(UserPersonalId.of(user.getPersonalId()),
                            messageSource.getMessage("error.user.already.exists", new List[]{},
                                    LocaleContextHolder.getLocale()));
                } catch (Exception e) {
                    response.addError(UserPersonalId.of(user.getPersonalId()),
                            messageSource.getMessage("error.user.unable.to.create", new List[]{},
                            LocaleContextHolder.getLocale()));
                }
            });
        } catch (Exception ex) {
            if (ex.getCause() instanceof CsvRequiredFieldEmptyException) {
                return errorBuilder.build(ex.getCause(), "error.fields.number.does.not.match", new List[]{},
                        HttpStatus.BAD_REQUEST);
            }
            return errorBuilder.build(ex, "error.bad.request", new List[]{},
                    HttpStatus.BAD_REQUEST);
        }

        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
