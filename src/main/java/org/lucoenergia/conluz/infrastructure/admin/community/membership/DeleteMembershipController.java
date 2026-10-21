package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.community.membership.DeleteMembershipService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
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

                    The membership of a user who owns supplies in the community is not deleted: the request is answered 409 with `MEMBERSHIP_OWNS_SUPPLIES`, and nothing is deleted. To stop their access, disable the membership instead (`POST /api/v1/communities/{communityId}/memberships/{userId}/disable`); to delete it, delete their supplies in the community first (`DELETE /api/v1/supplies/{supplyId}`). Supplies the user owns in other communities do not prevent it.
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
                    description = "The user owns supplies in the community, so the membership is not deleted, and nothing is. To stop their access, disable the membership instead; to delete it, delete their supplies in the community first.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = @ExampleObject(
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
                    )
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageMemberships(#communityId)")
    public void deleteMembership(@PathVariable("communityId") UUID communityId,
                                  @PathVariable("userId") UUID userId) {
        service.delete(communityId, userId);
    }
}
