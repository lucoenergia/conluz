package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.ManagePlatformAdminAccessService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class RevokePlatformAdminController {

    private final ManagePlatformAdminAccessService service;

    public RevokePlatformAdminController(ManagePlatformAdminAccessService service) {
        this.service = service;
    }

    @PostMapping(path = "/users/{userId}/revoke-platform-admin")
    @Operation(
            summary = "Revokes platform-admin privileges from a user by ID",
            description = """
                    This endpoint removes platform-administrator privileges from the user identified in the endpoint path.

                    Proper authentication, through an authentication token, is required for secure access.
                    **Required: Platform Admin. You cannot revoke your own privileges.**

                    Once the caller is allowed, the target is checked: an unknown user answers 404, and a user who is not a platform admin answers 409 USER_NOT_PLATFORM_ADMIN.

                    Two safety rails apply: a platform admin cannot revoke their own privileges, and the system can never be left without an enabled platform admin. Revoking the last enabled platform admin answers 409 USER_LAST_PLATFORM_ADMIN; since the caller is themselves an enabled platform admin, this can only happen when two revokes run at the same time. Revoking a disabled platform admin is always allowed, since it leaves the number of enabled platform admins as it is.

                    Upon a successful request, the server responds with an HTTP status code of 200, indicating that the user is no longer a platform administrator.

                    In situations where the operation encounters errors, the server responds with an appropriate error status code, accompanied by a descriptive error message to assist clients in diagnosing and resolving the issue.""",
            tags = ApiTag.USERS,
            operationId = "revokePlatformAdmin",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Platform-admin privileges revoked successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "The user does not exist.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                               "timestamp": "2024-01-03T10:10:25.534035352+01:00",
                                               "status": 404,
                                               "message": "User with ID '0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1' has not been found. Verify the ID is correct.",
                                               "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                               "errors": [
                                                 {
                                                   "message": "User with ID '0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1' has not been found. Verify the ID is correct.",
                                                   "code": null,
                                                   "params": null
                                                 }
                                               ]
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The request conflicts with the current state: the user is not a platform admin, or is the last enabled platform admin.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = {
                                    @ExampleObject(
                                            name = "USER_NOT_PLATFORM_ADMIN",
                                            description = "The user does not hold the platform-admin role.",
                                            value = """
                                                    {
                                                       "timestamp": "2024-01-03T10:10:25.534035352+01:00",
                                                       "status": 409,
                                                       "message": "User with ID '0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1' is not a platform admin.",
                                                       "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                                       "errors": [
                                                         {
                                                           "message": "User with ID '0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1' is not a platform admin.",
                                                           "code": "USER_NOT_PLATFORM_ADMIN",
                                                           "params": null
                                                         }
                                                       ]
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "USER_LAST_PLATFORM_ADMIN",
                                            description = "The user is the last enabled platform admin.",
                                            value = """
                                                    {
                                                       "timestamp": "2024-01-03T10:10:25.534035352+01:00",
                                                       "status": 409,
                                                       "message": "Cannot perform this action as it would leave the system with zero platform admins.",
                                                       "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                                       "errors": [
                                                         {
                                                           "message": "Cannot perform this action as it would leave the system with zero platform admins.",
                                                           "code": "USER_LAST_PLATFORM_ADMIN",
                                                           "params": null
                                                         }
                                                       ]
                                                    }
                                                    """
                                    )
                            }
                    )
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("@communityAccessGuard.canRevokePlatformAdmin(#userId)")
    public void revokePlatformAdmin(@PathVariable("userId") UUID userId) {
        service.revoke(UserId.of(userId));
    }
}
