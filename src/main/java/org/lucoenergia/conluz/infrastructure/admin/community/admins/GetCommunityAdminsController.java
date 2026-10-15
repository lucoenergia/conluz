package org.lucoenergia.conluz.infrastructure.admin.community.admins;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.community.admins.GetCommunityAdminsService;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
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

@RestController
@RequestMapping(
        value = "/api/v1/communities/{communityId}/admins",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class GetCommunityAdminsController {

    private final GetCommunityAdminsService service;

    public GetCommunityAdminsController(GetCommunityAdminsService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Lists a community's admins.",
            description = """
                    Every membership of the community with the COMMUNITY_ADMIN role, in any state, by full name: \
                    the name, contact email and phone from this community's member record, the DNI/NIE/NIF, and \
                    the state of the membership and of its invitation. Nothing about the community's members, and \
                    nothing from any other membership of the same people. Only a platform admin may list them.
                    """,
            tags = ApiTag.MEMBERSHIPS,
            operationId = "getCommunityAdmins",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "The community's admins",
                    useReturnTypeSchema = true
            )
    })
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("@communityAccessGuard.canManageCommunityAdmins(#communityId)")
    public List<CommunityAdminResponse> getCommunityAdmins(@PathVariable("communityId") UUID communityId) {
        return service.findByCommunityId(communityId).stream()
                .map(CommunityAdminResponse::new)
                .toList();
    }
}
