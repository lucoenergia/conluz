package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.membership.UpdateMembershipRoleService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
public class UpdateMembershipRoleController {

    private final UpdateMembershipRoleService service;

    public UpdateMembershipRoleController(UpdateMembershipRoleService service) {
        this.service = service;
    }

    @PatchMapping("/{userId}")
    @Operation(
            summary = "Updates the role of a membership.",
            description = "Changes the role of a user within a community. Requires COMMUNITY_ADMIN or PLATFORM_ADMIN.",
            tags = ApiTag.MEMBERSHIPS,
            operationId = "updateMembershipRole",
            security = @SecurityRequirement(name = "bearerToken", scopes = {"COMMUNITY_ADMIN"})
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Membership role updated successfully",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageMemberships(#id)")
    public MembershipResponse updateMembershipRole(@PathVariable("id") UUID id,
                                                    @PathVariable("userId") UUID userId,
                                                    @Valid @RequestBody UpdateMembershipRoleBody body) {
        CommunityMembership membership = service.updateRole(id, userId, body.getRole());
        return new MembershipResponse(membership);
    }
}
