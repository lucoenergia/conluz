package org.lucoenergia.conluz.infrastructure.admin.community.update;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.update.UpdateCommunityService;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.NotFoundErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(
        value = "/api/v1/communities",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class UpdateCommunityController {

    private final UpdateCommunityService service;

    public UpdateCommunityController(UpdateCommunityService service) {
        this.service = service;
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Updates an existing community.",
            description = """
                    Updates the details of an existing community.
                    Requires PLATFORM_ADMIN role.
                    """,
            tags = ApiTag.COMMUNITIES,
            operationId = "updateCommunity",
            security = @SecurityRequirement(name = "bearerToken", scopes = {"PLATFORM_ADMIN"})
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Community updated successfully",
                    useReturnTypeSchema = true
            )
    })
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @UnauthorizedErrorResponse
    @ForbiddenErrorResponse
    @NotFoundErrorResponse
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public CommunityResponse updateCommunity(@PathVariable("id") UUID id,
                                              @Valid @RequestBody UpdateCommunityBody body) {
        Community community = service.update(id, body.mapToCommunity());
        return new CommunityResponse(community);
    }
}
