package org.lucoenergia.conluz.infrastructure.admin.user.create;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserService;
import org.lucoenergia.conluz.infrastructure.admin.user.UserResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.UserCapabilitiesAssembler;

/**
 * Add a new user
 */
@RestController
@RequestMapping(
        value = "/api/v1/users",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
@Validated
public class CreateUserController {

    private final CreateUserService service;
    private final UserCapabilitiesAssembler capabilitiesAssembler;

    public CreateUserController(CreateUserService service,
                                UserCapabilitiesAssembler capabilitiesAssembler) {
        this.service = service;
        this.capabilitiesAssembler = capabilitiesAssembler;
    }

    @PostMapping
    @Operation(
            summary = "Creates a new user within the system.",
            description = """
                This endpoint facilitates the creation of a new user within the system.
                
                This endpoint requires clients to send a request containing essential user details, including username, password, and any additional relevant information.
                
                Optionally, a communityId and communityRole can be specified to automatically create a membership
                for the new user in the given community. If communityId is not provided and the caller is a
                COMMUNITY_ADMIN, the community is derived from the caller's active context. If the caller is a
                PLATFORM_ADMIN and communityId is not provided, a user with no memberships is created
                (they can be attached later).
                
                The `personalId` is normalised before it is stored or compared: surrounding and inner whitespace
                (including the no-break space), dots and hyphens are removed and letters are upper-cased, so
                `x1234567-l` is stored as `X1234567L`. If a user with the same normalised `personalId` already
                exists, the server responds 409 with the `USER_ALREADY_EXISTS` code; the error does not repeat
                the value.
                
                Authentication is mandated, utilizing an authentication token, to ensure secure access.
                **Required: Platform Admin, or Community Admin of the target community**
                
                Upon successful user creation, the server responds with an HTTP status code of 200, along with comprehensive details about the newly created user, such as a unique identifier and username.
                
                In cases where the creation process encounters errors, the server responds with an appropriate error status code, accompanied by a descriptive error message to guide clients in addressing and resolving the issue.
                """,
            tags = ApiTag.USERS,
            operationId = "createUser",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "User created successfully",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "A user with the same normalised personal ID already exists.",
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
    @PreAuthorize("@communityAccessGuard.canCreateUserIn(#body.communityId)")
    public UserResponse createUser(@AuthenticationPrincipal User currentUser,
                                   @Valid @RequestBody CreateUserBody body) {
        User user = service.create(body.mapToUser(), body.getCommunityId(), body.getCommunityRole());
        return new UserResponse(user,
                capabilitiesAssembler.assembleFetchingMemberships(currentUser, user));
    }
}
