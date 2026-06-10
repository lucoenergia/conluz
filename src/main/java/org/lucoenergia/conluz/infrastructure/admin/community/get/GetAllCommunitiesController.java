package org.lucoenergia.conluz.infrastructure.admin.community.get;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.community.CommunityWithStats;
import org.lucoenergia.conluz.domain.admin.community.access.CommunityAccessGuard;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityService;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/communities",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class GetAllCommunitiesController {

    private final GetCommunityService service;
    private final CommunityAccessGuard communityAccessGuard;

    public GetAllCommunitiesController(GetCommunityService service, CommunityAccessGuard communityAccessGuard) {
        this.service = service;
        this.communityAccessGuard = communityAccessGuard;
    }

    @GetMapping
    @Operation(
            summary = "Retrieves all communities visible to the current user.",
            description = """
                    Returns the list of communities the current user is allowed to see.
                    Platform admins see all communities. Regular users only see communities they belong to.
                    """,
            tags = ApiTag.COMMUNITIES,
            operationId = "getAllCommunities",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Communities retrieved successfully",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @PreAuthorize("isAuthenticated()")
    public List<CommunityResponse> getAllCommunities() {
        Set<UUID> visibleCommunityIds = communityAccessGuard.visibleCommunityIds();
        return service.findAllWithStats(visibleCommunityIds).stream()
                .map(CommunityResponse::new)
                .toList();
    }
}
