package org.lucoenergia.conluz.infrastructure.admin.community.admins;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.community.admins.InviteCommunityAdminService;
import org.lucoenergia.conluz.domain.admin.user.User;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/communities/{communityId}/admins/invite",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class InviteCommunityAdminController {

    private final InviteCommunityAdminService service;

    public InviteCommunityAdminController(InviteCommunityAdminService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(
            summary = "Invites a community admin.",
            description = """
                    Registers a person as an admin of the community with their DNI/NIE/NIF, full name, contact \
                    email and optional phone, and emails them an invitation to accept it at that contact email, in \
                    one operation. The membership is created pending, with the role COMMUNITY_ADMIN: it grants no \
                    access until it is accepted. The link is valid for 7 days. Only a platform admin may invite a \
                    community admin; a community admin may not.

                    The response is the same whether or not the person already had an account, and carries \
                    nothing from it. Inviting someone who already has a membership in this community, in any role \
                    or state, responds 409 with the `MEMBERSHIP_ALREADY_EXISTS` code, and nothing is created or \
                    sent.

                    If the server cannot send emails right now (no public web URL configured, or email sending \
                    disabled), it responds 503 with the `MEMBERSHIP_INVITATION_UNAVAILABLE` code before anything \
                    is created.
                    """,
            tags = ApiTag.MEMBERSHIPS,
            operationId = "inviteCommunityAdmin",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Community admin registered and invited",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The person already has a membership in this community (`MEMBERSHIP_ALREADY_EXISTS`).",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                               "timestamp": "2026-09-16T10:10:25.534035352+02:00",
                                               "status": 409,
                                               "message": "User with ID '0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1' is already a member of the community with ID '5c2f8f0e-3f4a-4a2b-9a1e-8c7d6e5f4a3b'.",
                                               "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                               "errors": [
                                                 {
                                                   "message": "User with ID '0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1' is already a member of the community with ID '5c2f8f0e-3f4a-4a2b-9a1e-8c7d6e5f4a3b'.",
                                                   "code": "MEMBERSHIP_ALREADY_EXISTS",
                                                   "params": {
                                                     "userId": "0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1",
                                                     "communityId": "5c2f8f0e-3f4a-4a2b-9a1e-8c7d6e5f4a3b"
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
                    description = "The server cannot send invitations right now (`MEMBERSHIP_INVITATION_UNAVAILABLE`). Nothing was created.",
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
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageCommunityAdmins(#communityId)")
    public CommunityAdminResponse inviteCommunityAdmin(@AuthenticationPrincipal User currentUser,
                                                       @PathVariable("communityId") UUID communityId,
                                                       @Valid @RequestBody InviteCommunityAdminBody body) {
        return new CommunityAdminResponse(service.invite(communityId, UserId.of(currentUser.getId()),
                body.toPersonalId(), body.toMemberRecord()));
    }
}
