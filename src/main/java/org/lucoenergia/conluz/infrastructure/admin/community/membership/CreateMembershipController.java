package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import io.swagger.v3.oas.annotations.Operation;
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
        value = "/api/v1/communities/{id}/memberships",
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
                    """,
            tags = ApiTag.MEMBERSHIPS,
            operationId = "createMembership",
            security = @SecurityRequirement(name = "bearerToken", scopes = {"COMMUNITY_ADMIN"})
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Membership created successfully",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageMemberships(#id)")
    public MembershipResponse createMembership(@PathVariable("id") UUID id,
                                                @Valid @RequestBody CreateMembershipBody body) {
        CommunityMembership membership = service.create(id, body.getUserId(), body.getRole());
        return new MembershipResponse(membership);
    }
}
