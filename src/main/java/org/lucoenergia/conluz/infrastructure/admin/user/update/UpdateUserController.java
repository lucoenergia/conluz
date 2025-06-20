package org.lucoenergia.conluz.infrastructure.admin.user.update;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.user.update.UpdateUserService;
import org.lucoenergia.conluz.infrastructure.admin.user.UserResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.*;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Updates an existing user
 */
@RestController
@RequestMapping("/api/v1")
@Validated
public class UpdateUserController {

    private final UpdateUserService service;

    public UpdateUserController(UpdateUserService service) {
        this.service = service;
    }

    @PutMapping("/users/{id}")
    @Operation(
            summary = "Updates user information",
            description = """
                This endpoint enables the update of user information by specifying the user's unique identifier in the endpoint path.
                
                Clients send a request containing the updated user details, and authentication, through an authentication token, is required for secure access.
                **Required Role: ADMIN**
                
                A successful update results in an HTTP status code of 200, indicating that the user information has been successfully modified. In cases where the update encounters errors, the server responds with an appropriate error status code along with a descriptive error message to assist clients in addressing and resolving the issue.
                
                If you don't provide some of the optional parameters, they will be considered as null value so their values will be updated with a null value.""",
            tags = ApiTag.USERS,
            operationId = "updateUser",
            security = @SecurityRequirement(name = "bearerToken", scopes = {"ADMIN"})
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "User successfully updated.",
                    useReturnTypeSchema = true
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse updateUser(@PathVariable("id") UUID userId, @Valid @RequestBody UpdateUserBody body) {
        return new UserResponse(service.update(body.toUser(userId)));
    }
}
