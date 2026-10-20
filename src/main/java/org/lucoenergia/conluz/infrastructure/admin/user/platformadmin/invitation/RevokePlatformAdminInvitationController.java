package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.invitation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.RevokePlatformAdminInvitationService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.PlatformAdminCapabilitiesAssembler;
import org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.PlatformAdminResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/platform-admins",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class RevokePlatformAdminInvitationController {

    private final RevokePlatformAdminInvitationService service;
    private final PlatformAdminCapabilitiesAssembler capabilitiesAssembler;

    public RevokePlatformAdminInvitationController(RevokePlatformAdminInvitationService service,
                                                   PlatformAdminCapabilitiesAssembler capabilitiesAssembler) {
        this.service = service;
        this.capabilitiesAssembler = capabilitiesAssembler;
    }

    @PostMapping("/{userId}/invitation/revoke")
    @Operation(
            summary = "Stops a platform-admin invitation's link from working.",
            description = """
                    Revokes the invitation's current link, whether or not it had expired. The invitation stays \
                    pending, listed with no valid link, and can be sent again with \
                    `resendPlatformAdminInvitation`. Works when the server cannot send emails.

                    An unknown user is answered 404, and a user without a pending invitation 409 with the \
                    `PLATFORM_ADMIN_NOT_INVITED` code. Only a platform admin may revoke.
                    """,
            tags = ApiTag.USERS,
            operationId = "revokePlatformAdminInvitation",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Link revoked: the invitation's row, still pending",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "The user does not exist.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The user has no pending invitation (`PLATFORM_ADMIN_NOT_INVITED`).",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = {
                                    @ExampleObject(
                                            name = "notInvited",
                                            value = """
                                                    {
                                                       "timestamp": "2026-10-20T10:10:25.534035352+02:00",
                                                       "status": 409,
                                                       "message": "User with ID '0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1' has no pending platform-admin invitation.",
                                                       "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                                       "errors": [
                                                         {
                                                           "message": "User with ID '0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1' has no pending platform-admin invitation.",
                                                           "code": "PLATFORM_ADMIN_NOT_INVITED",
                                                           "params": {
                                                             "userId": "0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1"
                                                           }
                                                         }
                                                       ]
                                                    }
                                                    """
                                    )
                            }
                    )
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @PreAuthorize("@communityAccessGuard.canManagePlatformAdminInvitations()")
    public PlatformAdminResponse revokePlatformAdminInvitation(@AuthenticationPrincipal User currentUser,
                                                               @PathVariable("userId") UUID userId) {
        return new PlatformAdminResponse(service.revoke(UserId.of(userId)),
                capabilitiesAssembler.assemble(currentUser, userId));
    }
}
