package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.invitation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.InvitePlatformAdminService;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.InvitedPlatformAdmin;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.PlatformAdminCapabilitiesAssembler;
import org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.PlatformAdminResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
        value = "/api/v1/platform-admins/invite",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class InvitePlatformAdminController {

    private final InvitePlatformAdminService service;
    private final PlatformAdminCapabilitiesAssembler capabilitiesAssembler;

    public InvitePlatformAdminController(InvitePlatformAdminService service,
                                         PlatformAdminCapabilitiesAssembler capabilitiesAssembler) {
        this.service = service;
        this.capabilitiesAssembler = capabilitiesAssembler;
    }

    @PostMapping
    @Operation(
            summary = "Invites a person to the platform-admin role.",
            description = """
                    Invites a person with their DNI/NIE/NIF, full name and email, and emails them a link to \
                    accept, valid for 7 days. Inviting grants nothing: the role is granted only when the person \
                    accepts with the link and the confirmation code in this response, which the inviter gives \
                    them by another channel, such as by phone or in person. The code is shown only in this \
                    response: no endpoint returns it again, and the email never contains it. Until the person \
                    accepts, the invitation is listed by `getPlatformAdmins` as pending, and never counts as a \
                    platform admin.

                    The account is found with the DNI/NIE/NIF, or created without a password. An existing account \
                    is never written, and the response is the same whether or not it existed: the row carries the \
                    name and email typed here, nothing from the account. Only a platform admin may invite.

                    The DNI/NIE/NIF may be typed in any case, with spaces, dots and hyphens. Once those are removed \
                    it must be a valid DNI, NIE or NIF; otherwise it responds 400 with the \
                    `USER_PERSONAL_ID_INVALID` code, and nothing is created or sent. A person who already holds \
                    the role is answered 409 with the `USER_ALREADY_PLATFORM_ADMIN` code, and one with a pending \
                    invitation 409 with the `PLATFORM_ADMIN_ALREADY_INVITED` code: resend that one instead.

                    If the server cannot send emails right now (no public web URL configured, or email sending \
                    disabled), it responds 503 with the `MEMBERSHIP_INVITATION_UNAVAILABLE` code before anything \
                    is looked up or created.
                    """,
            tags = ApiTag.USERS,
            operationId = "invitePlatformAdmin",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Person invited: the invitation's row and its confirmation code",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "The request is not valid. A body that breaks a validation rule is answered without "
                            + "a code. A `personalId` that is not a valid DNI, NIE or NIF once normalised is answered "
                            + "with the `USER_PERSONAL_ID_INVALID` code, and nothing is created or sent.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The person already holds the role (`USER_ALREADY_PLATFORM_ADMIN`), or already has "
                            + "a pending invitation (`PLATFORM_ADMIN_ALREADY_INVITED`). Nothing is created or sent.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = {
                                    @ExampleObject(
                                            name = "alreadyPlatformAdmin",
                                            value = """
                                                    {
                                                       "timestamp": "2026-10-20T10:10:25.534035352+02:00",
                                                       "status": 409,
                                                       "message": "User with ID '0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1' is already a platform admin.",
                                                       "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                                       "errors": [
                                                         {
                                                           "message": "User with ID '0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1' is already a platform admin.",
                                                           "code": "USER_ALREADY_PLATFORM_ADMIN",
                                                           "params": {
                                                             "userId": "0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1"
                                                           }
                                                         }
                                                       ]
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "alreadyInvited",
                                            value = """
                                                    {
                                                       "timestamp": "2026-10-20T10:10:25.534035352+02:00",
                                                       "status": 409,
                                                       "message": "User with ID '0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1' already has a pending platform-admin invitation.",
                                                       "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                                       "errors": [
                                                         {
                                                           "message": "User with ID '0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1' already has a pending platform-admin invitation.",
                                                           "code": "PLATFORM_ADMIN_ALREADY_INVITED",
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
                    responseCode = "503",
                    description = "The server cannot send invitations right now (`MEMBERSHIP_INVITATION_UNAVAILABLE`). Nothing was recorded.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class)
                    )
            )
    })
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @PreAuthorize("@communityAccessGuard.canManagePlatformAdminInvitations()")
    public InvitedPlatformAdminResponse invitePlatformAdmin(@AuthenticationPrincipal User currentUser,
                                                            @Valid @RequestBody InvitePlatformAdminBody body) {
        InvitedPlatformAdmin invited = service.invite(UserId.of(currentUser.getId()), body.toPersonalId(),
                body.getFullName(), body.getEmail());
        return new InvitedPlatformAdminResponse(new PlatformAdminResponse(invited.platformAdmin(),
                capabilitiesAssembler.assemble(currentUser, invited.platformAdmin().userId())),
                invited.confirmationCode().value());
    }
}
