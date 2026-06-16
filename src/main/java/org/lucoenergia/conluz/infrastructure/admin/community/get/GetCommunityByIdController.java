package org.lucoenergia.conluz.infrastructure.admin.community.get;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityService;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/communities",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class GetCommunityByIdController {

    private final GetCommunityService service;

    public GetCommunityByIdController(GetCommunityService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Retrieves a community by its ID.",
            description = """
                    Returns the community with the given ID.
                    Returns 404 if the community does not exist OR if the caller is not a member of it
                    (to avoid leaking community existence to non-members).
                    Platform admins always see the community if it exists.
                    """,
            tags = ApiTag.COMMUNITIES,
            operationId = "getCommunityById",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Community found",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("isAuthenticated() and @communityAccessGuard.canReadCommunity(#id)")
    public ResponseEntity<CommunityResponse> getCommunityById(@PathVariable("id") UUID id) {
        return service.findByIdWithStats(id)
                .map(c -> ResponseEntity.ok(new CommunityResponse(c)))
                .orElse(ResponseEntity.notFound().build());
    }
}
