package org.lucoenergia.conluz.infrastructure.admin.community.create;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityService;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ApiTag;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.BadRequestErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.ForbiddenErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.InternalServerErrorResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.apidocs.response.UnauthorizedErrorResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
        value = "/api/v1/communities",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
@Validated
public class CreateCommunityController {

    private final CreateCommunityService service;
    private final boolean multiCommunityEnabled;

    public CreateCommunityController(CreateCommunityService service,
                                     @Value("${conluz.multi-community.enabled}") boolean multiCommunityEnabled) {
        this.service = service;
        this.multiCommunityEnabled = multiCommunityEnabled;
    }

    @PostMapping
    @Operation(
            summary = "Creates a new energy community.",
            description = """
                    This endpoint facilitates the creation of a new energy community within the system.
                    Requires PLATFORM_ADMIN role.
                    When multi-community mode is disabled, this endpoint returns 404.
                    """,
            tags = ApiTag.COMMUNITIES,
            operationId = "createCommunity",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Community created successfully",
                    useReturnTypeSchema = true
            )
    })
    @ForbiddenErrorResponse
    @UnauthorizedErrorResponse
    @BadRequestErrorResponse
    @InternalServerErrorResponse
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<CommunityResponse> createCommunity(@Valid @RequestBody CreateCommunityBody body) {
        if (!multiCommunityEnabled) {
            return ResponseEntity.notFound().build();
        }
        Community community = service.create(body.mapToCommunity());
        return ResponseEntity.ok(new CommunityResponse(community));
    }
}
