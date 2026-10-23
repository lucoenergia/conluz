package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsService;
import org.lucoenergia.conluz.domain.admin.community.membership.MembershipInRoster;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.MembershipCapabilitiesAssembler;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.MembershipCapabilitiesResponse;

@RestController
@RequestMapping(
        value = "/api/v1/communities/{communityId}/memberships",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class GetMembershipsController {

    private final GetMembershipsService service;
    private final MembershipCapabilitiesAssembler capabilitiesAssembler;

    public GetMembershipsController(GetMembershipsService service,
                                    MembershipCapabilitiesAssembler capabilitiesAssembler) {
        this.service = service;
        this.capabilitiesAssembler = capabilitiesAssembler;
    }

    @GetMapping
    @Operation(
            summary = "Lists all memberships of a community.",
            description = "Returns the list of memberships for the specified community. Requires COMMUNITY_ADMIN of the community. Being a PLATFORM_ADMIN is not enough (#446): a platform admin who is not an admin of the community is answered 403.",
            tags = ApiTag.MEMBERSHIPS,
            operationId = "getMemberships",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Memberships retrieved successfully",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageMemberships(#communityId)")
    public List<MembershipResponse> getMemberships(@AuthenticationPrincipal User currentUser,
                                                   @PathVariable("communityId") UUID communityId) {
        List<MembershipInRoster> memberships = service.findByCommunityId(communityId);
        // One query for every member on this roster. The users join-fetched with the memberships
        // carry no memberships of their own, and canListSupplies needs those.
        Function<CommunityMembership, MembershipCapabilitiesResponse> capabilities =
                capabilitiesAssembler.assembleAll(currentUser,
                        memberships.stream().map(MembershipInRoster::membership).toList());

        return memberships.stream()
                .map(member -> new MembershipResponse(member, capabilities.apply(member.membership())))
                .toList();
    }
}
