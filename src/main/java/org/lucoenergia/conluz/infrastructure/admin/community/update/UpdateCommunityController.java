package org.lucoenergia.conluz.infrastructure.admin.community.update;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
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
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestError;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.CommunityCapabilitiesAssembler;

@RestController
@RequestMapping(
        value = "/api/v1/communities",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class UpdateCommunityController {

    private final UpdateCommunityService service;
    private final CommunityCapabilitiesAssembler capabilitiesAssembler;

    public UpdateCommunityController(UpdateCommunityService service,
                                     CommunityCapabilitiesAssembler capabilitiesAssembler) {
        this.capabilitiesAssembler = capabilitiesAssembler;
        this.service = service;
    }

    @PutMapping("/{communityId}")
    @Operation(
            summary = "Updates an existing community.",
            description = """
                    Updates the details of an existing community.
                    Requires PLATFORM_ADMIN role.

                    `code` and `legalId` are unique across communities. Moving either onto a value
                    another community already uses responds 409 with the
                    `COMMUNITY_ALREADY_EXISTS` code, whose `field` param names which of the two
                    collided. Leaving this community's own values unchanged is not a conflict.
                    """,
            tags = ApiTag.COMMUNITIES,
            operationId = "updateCommunity",
            security = @SecurityRequirement(name = "bearerToken")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Community updated successfully",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Another community already uses the given code or legal id.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RestError.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                               "timestamp": "2026-09-16T10:10:25.534035352+02:00",
                                               "status": 409,
                                               "message": "Another community already uses the code 'ACME-01'.",
                                               "traceId": "6e602860-80f7-4802-b20f-8b53fb011013",
                                               "errors": [
                                                 {
                                                   "message": "Another community already uses the code 'ACME-01'.",
                                                   "code": "COMMUNITY_ALREADY_EXISTS",
                                                   "params": {
                                                     "field": "code",
                                                     "value": "ACME-01"
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
    @PreAuthorize("@communityAccessGuard.canUpdateCommunity(#communityId)")
    public CommunityResponse updateCommunity(@AuthenticationPrincipal User currentUser,
                                             @PathVariable("communityId") UUID communityId,
                                             @Valid @RequestBody UpdateCommunityBody body) {
        Community community = service.update(communityId, body.mapToCommunity());
        return new CommunityResponse(community,
                capabilitiesAssembler.assemble(currentUser, communityId));
    }
}
