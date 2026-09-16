package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/communities/{communityId}/memberships",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class CreateMembershipController {

    private final CreateMembershipService service;

    public CreateMembershipController(CreateMembershipService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(
            summary = "Creates a new membership in a community.",
            description = """
                    Adds a user as a member of the specified community with the given role.
                    Requires COMMUNITY_ADMIN role in the community or PLATFORM_ADMIN.

                    A user can hold at most one membership per community, so adding one who is
                    already a member responds 409 with the `MEMBERSHIP_ALREADY_EXISTS` code. Use
                    the role PATCH to change an existing member's role.
                    """,
            tags = ApiTag.MEMBERSHIPS,
            operationId = "createMembership",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Membership created successfully",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The user is already a member of this community.",
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
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageMemberships(#communityId)")
    public MembershipResponse createMembership(@PathVariable("communityId") UUID communityId,
                                                @Valid @RequestBody CreateMembershipBody body) {
        CommunityMembership membership = service.create(communityId, body.getUserId(), body.getRole());
        return new MembershipResponse(membership);
    }
}
