package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.community.membership.DeleteMembershipService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/communities/{communityId}/memberships",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class DeleteMembershipController {

    private final DeleteMembershipService service;

    public DeleteMembershipController(DeleteMembershipService service) {
        this.service = service;
    }

    @DeleteMapping("/{userId}")
    @Operation(
            summary = "Removes a user from a community.",
            description = """
                    Deletes the membership of the specified user in the community, with its member record and its invitation tokens. Requires COMMUNITY_ADMIN of the community. Being a PLATFORM_ADMIN is not enough (#446): a platform admin who is not an admin of the community is answered 403.

                    The membership of the community's last active admin is not deleted, even when it is the caller's own: the request is answered 409 with `MEMBERSHIP_LAST_ACTIVE_ADMIN`, and nothing is deleted. An active admin is one whose membership is accepted and enabled and whose account is enabled; a pending or suspended admin does not count. Promote another active member to admin first (`PATCH /api/v1/communities/{communityId}/memberships/{userId}`). `lastActiveAdmin` on the membership says in advance whether this applies.

                    The membership of a user who owns supplies in the community is not deleted: the request is answered 409 with `MEMBERSHIP_OWNS_SUPPLIES`, and nothing is deleted. To stop their access, disable the membership instead (`POST /api/v1/communities/{communityId}/memberships/{userId}/disable`); to delete it, delete their supplies in the community first (`DELETE /api/v1/supplies/{supplyId}`). Supplies the user owns in other communities do not prevent it.

                    When both apply, the request is answered `MEMBERSHIP_LAST_ACTIVE_ADMIN`: deleting the supplies would not make the deletion possible.
                    """,
            tags = ApiTag.MEMBERSHIPS,
            operationId = "deleteMembership",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Membership deleted successfully",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The membership is not deleted, and nothing is: either it is the community's last active admin (`MEMBERSHIP_LAST_ACTIVE_ADMIN`; promote another active member to admin first), or the user owns supplies in the community (`MEMBERSHIP_OWNS_SUPPLIES`; to stop their access, disable the membership instead; to delete it, delete their supplies in the community first). The last-active-admin refusal answers first.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = {
                                    @ExampleObject(
                                            name = "MEMBERSHIP_LAST_ACTIVE_ADMIN",
                                            description = "The membership is the community's last active admin.",
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
                                    ),
                                    @ExampleObject(
                                            name = "MEMBERSHIP_OWNS_SUPPLIES",
                                            description = "The user owns supplies in the community.",
                                            value = """
                                                    {
                                                       "timestamp": "2026-10-17T10:10:25.534035352+02:00",
                                                       "status": 409,
                                                       "message": "The user with ID '0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1' owns supplies in the community with ID '5c2f8f0e-3f4a-4a2b-9a1e-8c7d6e5f4a3b', so their membership cannot be deleted. To stop their access, disable the membership instead; to delete it, delete their supplies in the community first.",
                                                       "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                                       "errors": [
                                                         {
                                                           "message": "The user with ID '0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1' owns supplies in the community with ID '5c2f8f0e-3f4a-4a2b-9a1e-8c7d6e5f4a3b', so their membership cannot be deleted. To stop their access, disable the membership instead; to delete it, delete their supplies in the community first.",
                                                           "code": "MEMBERSHIP_OWNS_SUPPLIES",
                                                           "params": {
                                                             "userId": "0b8dc1a4-9e6b-4e4a-9a3e-2ab5a4a0f0a1",
                                                             "communityId": "5c2f8f0e-3f4a-4a2b-9a1e-8c7d6e5f4a3b"
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
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageMemberships(#communityId)")
    public void deleteMembership(@AuthenticationPrincipal User currentUser,
                                  @PathVariable("communityId") UUID communityId,
                                  @PathVariable("userId") UUID userId) {
        service.delete(communityId, userId, UserId.of(currentUser.getId()));
    }
}
