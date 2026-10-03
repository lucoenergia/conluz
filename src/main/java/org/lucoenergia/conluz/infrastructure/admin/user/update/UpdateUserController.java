package org.lucoenergia.conluz.infrastructure.admin.user.update;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.user.update.UpdateUserService;
import org.lucoenergia.conluz.infrastructure.admin.user.UserResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.*;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.UserCapabilitiesAssembler;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.UserCapabilitiesResponse;

/**
 * Updates an existing user
 */
@RestController
@RequestMapping("/api/v1")
@Validated
public class UpdateUserController {

    private final UpdateUserService service;
    private final UserCapabilitiesAssembler capabilitiesAssembler;

    public UpdateUserController(UpdateUserService service,
                                UserCapabilitiesAssembler capabilitiesAssembler) {
        this.service = service;
        this.capabilitiesAssembler = capabilitiesAssembler;
    }

    @PutMapping("/users/{userId}")
    @Operation(
            summary = "Updates user information",
            description = """
                This endpoint enables the update of user information by specifying the user's unique identifier in the endpoint path.
                
                Clients send a request containing the updated user details, and authentication, through an authentication token, is required for secure access.
                **Required: Platform Admin or Community Admin**
                
                A successful update results in an HTTP status code of 200, indicating that the user information has been successfully modified. In cases where the update encounters errors, the server responds with an appropriate error status code along with a descriptive error message to assist clients in addressing and resolving the issue.
                
                The `personalId` is normalised before it is stored or compared: surrounding and inner whitespace (including the no-break space), dots and hyphens are removed and letters are upper-cased. If another user already has the same normalised `personalId`, the server responds 409 with the `USER_ALREADY_EXISTS` code; the error does not repeat the value.
                
                If you don't provide some of the optional parameters, they will be considered as null value so their values will be updated with a null value.""",
            tags = ApiTag.USERS,
            operationId = "updateUser",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "User successfully updated.",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Another user already has the same normalised personal ID.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                               "timestamp": "2026-10-03T10:10:25.534035352+02:00",
                                               "status": 409,
                                               "message": "A user with this personal ID already exists.",
                                               "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                               "errors": [
                                                 {
                                                   "message": "A user with this personal ID already exists.",
                                                   "code": "USER_ALREADY_EXISTS",
                                                   "params": null
                                                 }
                                               ]
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
    @PreAuthorize("@communityAccessGuard.canEditUser(#userId)")
    public UserResponse updateUser(@AuthenticationPrincipal User currentUser,
                                   @PathVariable("userId") UUID userId,
                                   @Valid @RequestBody UpdateUserBody body) {
        // Settled before the write rather than after. Editing a user's number, DNI, name or contact
        // details cannot change anybody's community memberships, so the decision is identical either
        // way -- and asking for the target's memberships afterwards would auto-flush the pending
        // update (the query join touches `users`), turning any failure of the write itself into a
        // 500 raised from inside this method rather than at the end of the request.
        UserCapabilitiesResponse capabilities =
                capabilitiesAssembler.assembleFetchingMemberships(currentUser, userId);
        User updated = service.update(body.toUser(userId));
        return new UserResponse(updated, capabilities);
    }
}
