package org.lucoenergia.conluz.infrastructure.admin.user.update;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.lucoenergia.conluz.domain.admin.user.update.UpdateUserService;
import org.lucoenergia.conluz.infrastructure.admin.user.UserResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Updates an existing user
 */
@RestController
@RequestMapping("/api/v1")
public class UpdateUserController {

    private final UpdateUserAssembler assembler;
    private final UpdateUserService service;

    public UpdateUserController(UpdateUserAssembler assembler, UpdateUserService service) {
        this.assembler = assembler;
        this.service = service;
    }

    @PutMapping("/users/{id}")
    @Operation(
            summary = "Updates user information",
            description = "This endpoint enables the update of user information by specifying the user's unique identifier in the endpoint path. Clients send a request containing the updated user details, and authentication, through an authentication token, is required for secure access. A successful update results in an HTTP status code of 200, indicating that the user information has been successfully modified. In cases where the update encounters errors, the server responds with an appropriate error status code along with a descriptive error message to assist clients in addressing and resolving the issue.",
            tags = ApiTag.USERS,
            operationId = "updateUser"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "User successfully updated.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "id": "1bba9d48-a0c8-4dac-bf81-e06106ad7b4a",
                                              "personalId": "12345678Z",
                                              "number": 2,
                                              "fullName": "Alice Smith",
                                              "address": "Fake Street 666",
                                              "email": "alicesmith@email.com",
                                              "phoneNumber": "+34666555111",
                                              "enabled": true,
                                              "role": "PARTNER"
                                            }
                                            """
                            )
                    )
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @NotFoundErrorResponse
    public UserResponse updateUser(@PathVariable("id") UUID userId, @RequestBody UpdateUserBody body) {
        return new UserResponse(service.update(assembler.assemble(userId, body)));
    }
}
