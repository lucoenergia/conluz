package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.community.membership.MembershipInRoster;
import org.lucoenergia.conluz.domain.admin.community.membership.UpdateMembershipRoleService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.MembershipCapabilitiesAssembler;

@RestController
@RequestMapping(
        value = "/api/v1/communities/{communityId}/memberships",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class UpdateMembershipRoleController {

    private final UpdateMembershipRoleService service;
    private final MembershipCapabilitiesAssembler capabilitiesAssembler;

    public UpdateMembershipRoleController(UpdateMembershipRoleService service,
                                    MembershipCapabilitiesAssembler capabilitiesAssembler) {
        this.service = service;
        this.capabilitiesAssembler = capabilitiesAssembler;
    }

    @PatchMapping("/{userId}")
    @Operation(
            summary = "Updates the role of a membership.",
            description = """
                    Changes the role of a user within a community. Requires COMMUNITY_ADMIN of the community. Being a PLATFORM_ADMIN is not enough (#446): a platform admin who is not an admin of the community is answered 403.

                    Giving a membership the `COMMUNITY_ADMIN` role requires a contact email on its member record: \
                    without one it responds 400 with the `MEMBERSHIP_WITHOUT_CONTACT_EMAIL` code, and the role is \
                    left as it was.

                    Demoting the community's last active admin to `COMMUNITY_MEMBER`, even when it is the caller \
                    themselves, responds 409 with the `MEMBERSHIP_LAST_ACTIVE_ADMIN` code, and the role is left as it \
                    was. An active admin is one whose membership is accepted and enabled and whose account is enabled; \
                    a pending or suspended admin does not count. Promote another active member to admin first. \
                    `lastActiveAdmin` on the membership says in advance whether this applies.
                    """,
            tags = ApiTag.MEMBERSHIPS,
            operationId = "updateMembershipRole",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Membership role updated successfully",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The membership is the community's last active admin, so it is not demoted, and nothing is changed. Promote another active member to admin first.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                               "timestamp": "2026-10-23T10:10:25.534035352+02:00",
                                               "status": 409,
                                               "message": "The user with ID '0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1' is the last active admin of the community with ID '5c2f8f0e-3f4a-4a2b-9a1e-8c7d6e5f4a3b', so they cannot be made a member or removed from it. Promote another active member to admin first.",
                                               "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                               "errors": [
                                                 {
                                                   "message": "The user with ID '0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1' is the last active admin of the community with ID '5c2f8f0e-3f4a-4a2b-9a1e-8c7d6e5f4a3b', so they cannot be made a member or removed from it. Promote another active member to admin first.",
                                                   "code": "MEMBERSHIP_LAST_ACTIVE_ADMIN",
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
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageMemberships(#communityId)")
    public MembershipResponse updateMembershipRole(@AuthenticationPrincipal User currentUser,
                                                   @PathVariable("communityId") UUID communityId,
                                                    @PathVariable("userId") UUID userId,
                                                    @Valid @RequestBody UpdateMembershipRoleBody body) {
        MembershipInRoster updated = service.updateRole(communityId, userId, body.getRole());
        return new MembershipResponse(updated, capabilitiesAssembler.assemble(currentUser, updated.membership()));
    }
}
