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
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
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

    @PostMapping(path = "/users/{id}/revoke-platform-admin")
    @Operation(
            summary = "Revokes platform-admin privileges from a user by ID",
            description = """
                    This endpoint removes platform-administrator privileges from the user identified in the endpoint path.

                    Proper authentication, through an authentication token, is required for secure access.
                    **Required: Platform Admin. You cannot revoke your own privileges.**

                    Two safety rails apply: the system can never be left with zero platform admins (the last platform admin cannot be revoked), and a platform admin cannot revoke their own privileges.

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
                    responseCode = "409",
                    description = "The request conflicts with the current state: the last platform admin cannot be revoked.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                               "timestamp": "2024-01-03T10:10:25.534035352+01:00",
                                               "status": 409,
                                               "message": "Cannot revoke the last platform administrator.",
                                               "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                               "errors": [
                                                 {
                                                   "message": "Cannot revoke the last platform administrator.",
                                                   "code": "USER_LAST_PLATFORM_ADMIN",
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
    @NotFoundErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("hasRole('PLATFORM_ADMIN') and !@communityAccessGuard.isCurrentUser(#userId)")
    public void revokePlatformAdmin(@PathVariable("id") UUID userId) {
        service.revoke(UserId.of(userId));
    }
}
