package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.invitation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.InvitedPlatformAdmin;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.ResendPlatformAdminInvitationService;
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
public class ResendPlatformAdminInvitationController {

    private final ResendPlatformAdminInvitationService service;
    private final PlatformAdminCapabilitiesAssembler capabilitiesAssembler;

    public ResendPlatformAdminInvitationController(ResendPlatformAdminInvitationService service,
                                                   PlatformAdminCapabilitiesAssembler capabilitiesAssembler) {
        this.service = service;
        this.capabilitiesAssembler = capabilitiesAssembler;
    }

    @PostMapping("/{userId}/invitation/resend")
    @Operation(
            summary = "Sends a platform-admin invitation again, with a new link and a new confirmation code.",
            description = """
                    Emails the person a new invitation link, at the address the invitation was sent to, and \
                    generates a new confirmation code, returned only in this response. The previous link and the \
                    previous code stop working, and the count of wrong codes starts again. At most 4 sends in any \
                    24 hours, the first one included: further resends are answered 429 with the \
                    `PLATFORM_ADMIN_INVITATION_RESEND_LIMIT_REACHED` code and a `Retry-After` header.

                    An unknown user is answered 404, and a user without a pending invitation 409 with the \
                    `PLATFORM_ADMIN_NOT_INVITED` code. If the server cannot send emails right now, it responds \
                    503 with the `MEMBERSHIP_INVITATION_UNAVAILABLE` code before anything is recorded. Only a \
                    platform admin may resend.
                    """,
            tags = ApiTag.USERS,
            operationId = "resendPlatformAdminInvitation",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Invitation sent again: its row and its new confirmation code",
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
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = """
                            The invitation has been sent 4 times in the last 24 hours \
                            (`PLATFORM_ADMIN_INVITATION_RESEND_LIMIT_REACHED`). Nothing was recorded.""",
                    headers = @Header(
                            name = "Retry-After",
                            description = "Whole seconds until a resend will be accepted again.",
                            required = true,
                            schema = @Schema(type = "integer", format = "int64")
                    ),
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                               "timestamp": "2026-10-20T10:10:25.534035352+02:00",
                                               "status": 429,
                                               "message": "The platform-admin invitation of user with ID '0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1' has been sent as many times as allowed in the last 24 hours. Try again in 3600 seconds.",
                                               "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                               "errors": [
                                                 {
                                                   "message": "The platform-admin invitation of user with ID '0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1' has been sent as many times as allowed in the last 24 hours. Try again in 3600 seconds.",
                                                   "code": "PLATFORM_ADMIN_INVITATION_RESEND_LIMIT_REACHED",
                                                   "params": {
                                                     "userId": "0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1",
                                                     "retryAfterSeconds": "3600"
                                                   }
                                                 }
                                               ]
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "The server cannot send invitations right now (`MEMBERSHIP_INVITATION_UNAVAILABLE`). Nothing was recorded.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class)
                    )
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @PreAuthorize("@communityAccessGuard.canManagePlatformAdminInvitations()")
    public InvitedPlatformAdminResponse resendPlatformAdminInvitation(@AuthenticationPrincipal User currentUser,
                                                                      @PathVariable("userId") UUID userId) {
        InvitedPlatformAdmin invited = service.resend(UserId.of(userId));
        return new InvitedPlatformAdminResponse(new PlatformAdminResponse(invited.platformAdmin(),
                capabilitiesAssembler.assemble(currentUser, userId)), invited.confirmationCode().value());
    }
}
