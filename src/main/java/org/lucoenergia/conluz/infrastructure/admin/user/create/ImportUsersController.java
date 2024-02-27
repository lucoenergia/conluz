package org.lucoenergia.conluz.infrastructure.admin.user.create;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;

/**
 * Add a new user
 */
@RestController
@RequestMapping(
        value = "/api/v1/users/import",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
@Validated
public class ImportUsersController {

    private final CreateUserService service;

    public ImportUsersController(CreateUserService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(
            summary = "Creates a new user within the system.",
            description = """
                This endpoint facilitates the creation of a new user within the system.
                
                This endpoint requires clients to send a request containing essential user details, including username, password, and any additional relevant information.
                
                Authentication is mandated, utilizing an authentication token, to ensure secure access.
                
                Upon successful user creation, the server responds with an HTTP status code of 200, along with comprehensive details about the newly created user, such as a unique identifier and username.
                
                In cases where the creation process encounters errors, the server responds with an appropriate error status code, accompanied by a descriptive error message to guide clients in addressing and resolving the issue.
                """,
            tags = ApiTag.USERS,
            operationId = "createUser"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "User created successfully",
                    useReturnTypeSchema = true
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    public ResponseEntity<String> createUsersWithFile(@RequestParam("file") MultipartFile file) {

        try (Reader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {

            // create csv bean reader
            CsvToBean<CreateUserBody> csvToBean = new CsvToBeanBuilder<CreateUserBody>(reader)
                    .withType(CreateUserBody.class)
                    .withIgnoreLeadingWhiteSpace(true)
                    .build();

            // convert `CsvToBean` object to list of users
            List<CreateUserBody> users = csvToBean.parse();

            // save users in DB ?
            users.forEach(user -> System.out.println(user.toString()));

        } catch(Exception ex) {
            // error handling
        }

        return new ResponseEntity<>("CSV file has been successfully uploaded", HttpStatus.OK);
    }
}
